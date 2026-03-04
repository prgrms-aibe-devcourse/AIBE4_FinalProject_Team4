package kr.java.documind.global.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.service.MemberService;
import kr.java.documind.global.config.JwtProperties;
import kr.java.documind.global.security.RedisTokenService;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import kr.java.documind.global.security.jwt.TokenProvider;
import kr.java.documind.global.util.CookieUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final TokenProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final CookieUtil cookieUtil;
    private final RedisTokenService redisTokenService;
    private final MemberService memberService;
    private final HttpCookieOAuth2AuthorizationRequestRepository authRequestRepository;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {

        CustomUserDetails authMember = (CustomUserDetails) authentication.getPrincipal();

        Member member = memberService.getMemberWithCompany(authMember.getMemberId());

        if (!member.isActive()) {
            log.warn("정지된 계정 로그인 시도: memberId={}", member.getId());
            authRequestRepository.removeAuthorizationRequestCookies(request, response);
            deletePendingRoleCookie(response);
            response.sendRedirect("/auth/login?error=account_suspended");
            return;
        }

        String accessToken =
                jwtProvider.generateAccessToken(member.getId(), member.getGlobalRole());
        String refreshToken =
                jwtProvider.generateRefreshToken(member.getId(), member.getGlobalRole());

        boolean secure = jwtProperties.isCookieSecure();
        cookieUtil.addCookie(
                response,
                jwtProperties.getAccessCookieName(),
                accessToken,
                jwtProperties.getAccessExpirationSeconds(),
                secure);
        cookieUtil.addCookie(
                response,
                jwtProperties.getRefreshCookieName(),
                refreshToken,
                jwtProperties.getRefreshExpirationSeconds(),
                secure);

        redisTokenService.saveRefreshToken(
                member.getId(), refreshToken, jwtProperties.getRefreshExpirationSeconds());

        authRequestRepository.removeAuthorizationRequestCookies(request, response);
        deletePendingRoleCookie(response);
        clearAuthenticationAttributes(request);

        log.info("OAuth2 로그인 성공: memberId={} role={}", member.getId(), member.getGlobalRole());
        response.sendRedirect("/dashboard");
    }

    private void deletePendingRoleCookie(HttpServletResponse response) {
        cookieUtil.deleteCookie(
                response,
                CustomOAuth2UserService.PENDING_ROLE_COOKIE,
                jwtProperties.isCookieSecure());
    }
}

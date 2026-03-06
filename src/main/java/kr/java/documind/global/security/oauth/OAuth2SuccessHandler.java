package kr.java.documind.global.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.CompanyStatus;
import kr.java.documind.domain.member.model.enums.GlobalRole;
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

    private static final Set<String> ALLOWED_REDIRECT_PATHS =
            Set.of("/member/dashboard", "/my/company");

    private static final String URL_DASHBOARD = "/member/dashboard";
    private static final String URL_COMPANY = "/my/company";

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
            log.warn("[OAuth2SuccessHandler] 정지된 계정 로그인 시도: memberId={}", member.getId());
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

        GlobalRole selectedRole = resolveRoleFromCookie(request);
        boolean roleMismatch = false;
        if (selectedRole != null && selectedRole != member.getGlobalRole()) {
            log.info(
                    "[OAuth2SuccessHandler] 역할 불일치 감지: selected={}, actual={}",
                    selectedRole,
                    member.getGlobalRole());
            roleMismatch = true;
        }

        authRequestRepository.removeAuthorizationRequestCookies(request, response);
        deletePendingRoleCookie(response);
        deleteAllowEmailDuplicateCookie(response);
        clearAuthenticationAttributes(request);

        String redirectUrl = resolveRedirectUrl(member);

        if (roleMismatch) {
            String toastValue = member.isCeo() ? "role_mismatch_ceo" : "role_mismatch_employee";
            redirectUrl += (redirectUrl.contains("?") ? "&" : "?") + "toast_message=" + toastValue;
        }

        log.info(
                "[OAuth2SuccessHandler] OAuth2 로그인 성공: memberId={} role={} redirect={}",
                member.getId(),
                member.getGlobalRole(),
                redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    private String resolveRedirectUrl(Member member) {
        String resolved;

        if (member.isAdmin()) {
            resolved = URL_COMPANY;
        } else if (member.isCeo()) {
            Company company = member.getCompany();
            boolean approved = company != null && company.getStatus() == CompanyStatus.APPROVED;
            resolved = approved ? URL_DASHBOARD : URL_COMPANY;
        } else {
            resolved = URL_DASHBOARD;
        }

        if (!ALLOWED_REDIRECT_PATHS.contains(resolved)) {
            log.error(
                    "[OAuth2SuccessHandler] 허용되지 않은 리다이렉트 경로 차단 (allowlist fallback): {}",
                    resolved);
            return URL_DASHBOARD;
        }
        return resolved;
    }

    private void deletePendingRoleCookie(HttpServletResponse response) {
        cookieUtil.deleteCookie(
                response,
                CustomOAuth2UserService.PENDING_ROLE_COOKIE,
                jwtProperties.isCookieSecure());
    }

    private void deleteAllowEmailDuplicateCookie(HttpServletResponse response) {
        cookieUtil.deleteCookie(
                response,
                CustomOAuth2UserService.ALLOW_EMAIL_DUPLICATE_COOKIE,
                jwtProperties.isCookieSecure());
    }

    private GlobalRole resolveRoleFromCookie(HttpServletRequest request) {
        return cookieUtil
                .getCookieValue(request, CustomOAuth2UserService.PENDING_ROLE_COOKIE)
                .map(
                        value -> {
                            try {
                                return GlobalRole.valueOf(value.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                return null;
                            }
                        })
                .orElse(null);
    }
}

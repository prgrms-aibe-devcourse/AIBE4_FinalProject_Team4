package kr.java.documind.domain.member.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.GlobalRole;
import kr.java.documind.domain.member.service.MemberService;
import kr.java.documind.global.config.JwtProperties;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.response.ErrorResponse;
import kr.java.documind.global.security.RedisTokenService;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import kr.java.documind.global.security.jwt.TokenProvider;
import kr.java.documind.global.util.CookieUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final TokenProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final CookieUtil cookieUtil;
    private final RedisTokenService redisTokenService;
    private final MemberService memberService;

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            HttpServletRequest request, HttpServletResponse response) {

        String refreshToken =
                cookieUtil
                        .getCookieValue(request, jwtProperties.getRefreshCookieName())
                        .orElse(null);

        if (refreshToken == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(ErrorResponse.of("Refresh Token이 없습니다.")));
        }

        if (!jwtProvider.validateToken(refreshToken)) {
            log.debug("만료 또는 유효하지 않은 Refresh Token");
            deleteAuthCookies(response);
            return ResponseEntity.status(401)
                    .body(
                            ApiResponse.error(
                                    ErrorResponse.of("Refresh Token이 만료되었습니다. 다시 로그인하세요.")));
        }

        UUID memberId = jwtProvider.getMemberId(refreshToken);
        GlobalRole globalRole = jwtProvider.getGlobalRole(refreshToken);

        String storedToken = redisTokenService.consumeRefreshToken(memberId);
        if (!refreshToken.equals(storedToken)) {
            log.warn("Refresh Token 불일치 — 탈취 가능성: memberId={}", memberId);
            deleteAuthCookies(response);
            return ResponseEntity.status(401)
                    .body(
                            ApiResponse.error(
                                    ErrorResponse.of("유효하지 않은 Refresh Token입니다. 다시 로그인하세요.")));
        }

        Member member = memberService.getMemberWithCompany(memberId);
        if (!member.isActive()) {
            log.warn(
                    "[AuthApiController] 비활성 계정의 토큰 갱신 시도: memberId={} status={}",
                    memberId,
                    member.getAccountStatus());
            deleteAuthCookies(response);
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(ErrorResponse.of("계정이 비활성화되었습니다. 다시 로그인하세요.")));
        }

        String newAccessToken = jwtProvider.generateAccessToken(memberId, globalRole);
        String newRefreshToken = jwtProvider.generateRefreshToken(memberId, globalRole);

        boolean secure = jwtProperties.isCookieSecure();
        cookieUtil.addCookie(
                response,
                jwtProperties.getAccessCookieName(),
                newAccessToken,
                jwtProperties.getAccessExpirationSeconds(),
                secure);
        cookieUtil.addCookie(
                response,
                jwtProperties.getRefreshCookieName(),
                newRefreshToken,
                jwtProperties.getRefreshExpirationSeconds(),
                secure);

        redisTokenService.saveRefreshToken(
                memberId, newRefreshToken, jwtProperties.getRefreshExpirationSeconds());

        log.debug("[AuthApiController] Access Token 재발급 완료: memberId={}", memberId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal CustomUserDetails authMember,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (authMember != null) {
            String accessToken =
                    cookieUtil
                            .getCookieValue(request, jwtProperties.getAccessCookieName())
                            .orElse(null);

            if (accessToken != null) {
                long remainingMillis = jwtProvider.getRemainingMillis(accessToken);
                if (remainingMillis > 0) {
                    redisTokenService.addToBlacklist(accessToken, remainingMillis);
                }
            }

            redisTokenService.deleteRefreshToken(authMember.getMemberId());
            log.info("[AuthApiController] 로그아웃: memberId={}", authMember.getMemberId());

        } else {
            String refreshToken =
                    cookieUtil
                            .getCookieValue(request, jwtProperties.getRefreshCookieName())
                            .orElse(null);

            if (refreshToken != null) {
                try {
                    UUID memberId = jwtProvider.getMemberIdFromExpiredToken(refreshToken);
                    redisTokenService.deleteRefreshToken(memberId);
                    log.info(
                            "[AuthApiController] 만료된 토큰으로 로그아웃 처리 (Refresh Token 정리): memberId={}",
                            memberId);
                } catch (Exception e) {
                    log.debug(
                            "Refresh Token에서 memberId 추출 실패 (이미 정리되었거나 서명 오류): {}", e.getMessage());
                }
            }
        }

        deleteAuthCookies(response);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private void deleteAuthCookies(HttpServletResponse response) {
        boolean secure = jwtProperties.isCookieSecure();
        cookieUtil.deleteCookie(response, jwtProperties.getAccessCookieName(), secure);
        cookieUtil.deleteCookie(response, jwtProperties.getRefreshCookieName(), secure);
    }
}

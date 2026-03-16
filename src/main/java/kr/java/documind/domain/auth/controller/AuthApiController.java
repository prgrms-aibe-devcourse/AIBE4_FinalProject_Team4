package kr.java.documind.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import kr.java.documind.domain.auth.service.AuthService;
import kr.java.documind.global.config.JwtProperties;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.security.jwt.CustomUserDetails;
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

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final CookieUtil cookieUtil;

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            HttpServletRequest request, HttpServletResponse response) {

        String refreshToken = getCookieValue(request, jwtProperties.getRefreshCookieName());

        AuthService.AuthTokens newTokens = authService.refresh(refreshToken);
        addAuthCookies(response, newTokens);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal CustomUserDetails authMember,
            HttpServletRequest request,
            HttpServletResponse response) {

        String accessToken = getCookieValue(request, jwtProperties.getAccessCookieName());
        String refreshToken = getCookieValue(request, jwtProperties.getRefreshCookieName());
        UUID memberId = authMember != null ? authMember.getMemberId() : null;

        authService.logout(accessToken, refreshToken, memberId);

        deleteAuthCookies(response);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private String getCookieValue(HttpServletRequest request, String cookieName) {
        return cookieUtil.getCookieValue(request, cookieName).orElse(null);
    }

    private void addAuthCookies(HttpServletResponse response, AuthService.AuthTokens authTokens) {
        boolean secure = jwtProperties.isCookieSecure();

        cookieUtil.addCookie(
                response,
                jwtProperties.getAccessCookieName(),
                authTokens.accessToken(),
                jwtProperties.getAccessExpirationSeconds(),
                secure);

        cookieUtil.addCookie(
                response,
                jwtProperties.getRefreshCookieName(),
                authTokens.refreshToken(),
                jwtProperties.getRefreshExpirationSeconds(),
                secure);
    }

    private void deleteAuthCookies(HttpServletResponse response) {
        boolean secure = jwtProperties.isCookieSecure();
        cookieUtil.deleteCookie(response, jwtProperties.getAccessCookieName(), secure);
        cookieUtil.deleteCookie(response, jwtProperties.getRefreshCookieName(), secure);
    }
}

package kr.java.documind.global.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final HttpCookieOAuth2AuthorizationRequestRepository authRequestRepository;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {

        authRequestRepository.removeAuthorizationRequestCookies(request, response);
        log.warn(
                "[OAuth2FailureHandler] OAuth2 로그인 실패: reason={} ip={}",
                exception.getMessage(),
                getClientIp(request));

        if (exception instanceof OAuth2AuthenticationException oauthEx) {
            String errorCode = oauthEx.getError().getErrorCode();

            if (CustomOAuth2UserService.EMAIL_CONFLICT_ERROR.equals(errorCode)) {
                // description 형식: "GOOGLE||nickname||email||profileImageUrl||ROLE"
                // (nickname, email, profileImageUrl 은 URLEncoder.encode 된 값)
                String desc = oauthEx.getError().getDescription();
                String[] parts = desc != null ? desc.split("\\|\\|", -1) : new String[0];
                String existingProvider = parts.length > 0 ? parts[0].toLowerCase() : "unknown";
                String nickname = parts.length > 1 ? parts[1] : "";
                String email = parts.length > 2 ? parts[2] : "";
                String profileImage = parts.length > 3 ? parts[3] : "";
                String existingRole = parts.length > 4 ? parts[4].toLowerCase() : "";

                response.sendRedirect(
                        "/auth/login?error=email_conflict"
                                + "&existing_provider="
                                + existingProvider
                                + "&nickname="
                                + nickname
                                + "&email="
                                + email
                                + "&profile_image="
                                + profileImage
                                + "&existing_role="
                                + existingRole);
                return;
            }

            if (CustomOAuth2UserService.ROLE_CONFLICT_ERROR.equals(errorCode)) {
                // description 형식: "CEO:google" 또는 "EMPLOYEE:github"
                String desc = oauthEx.getError().getDescription();
                String[] parts = desc != null ? desc.split(":") : new String[0];
                String existingRole = parts.length > 0 ? parts[0].toLowerCase() : "unknown";
                String provider = parts.length > 1 ? parts[1] : "unknown";
                response.sendRedirect(
                        "/auth/login?error=role_conflict&existing_role="
                                + existingRole
                                + "&provider="
                                + provider);
                return;
            }
        }

        String encodedError = URLEncoder.encode("소셜 로그인에 실패했습니다.", StandardCharsets.UTF_8);
        response.sendRedirect("/auth/login?error=" + encodedError);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

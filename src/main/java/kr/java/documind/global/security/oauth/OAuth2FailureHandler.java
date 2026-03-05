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
        log.warn("OAuth2 로그인 실패: reason={} ip={}", exception.getMessage(), getClientIp(request));

        if (exception instanceof OAuth2AuthenticationException oauthEx) {
            String errorCode = oauthEx.getError().getErrorCode();

            if (CustomOAuth2UserService.EMAIL_CONFLICT_ERROR.equals(errorCode)) {
                String existingProvider =
                        oauthEx.getError().getDescription() != null
                                ? oauthEx.getError().getDescription().toLowerCase()
                                : "unknown";
                response.sendRedirect(
                        "/auth/login?error=email_conflict&existing_provider=" + existingProvider);
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

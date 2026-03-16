package kr.java.documind.domain.auth.exception;

import jakarta.servlet.http.HttpServletResponse;
import kr.java.documind.domain.auth.controller.AuthApiController;
import kr.java.documind.global.config.JwtProperties;
import kr.java.documind.global.exception.BusinessException;
import kr.java.documind.global.exception.ForbiddenException;
import kr.java.documind.global.exception.UnauthorizedException;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.response.ErrorResponse;
import kr.java.documind.global.util.CookieUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = AuthApiController.class)
@RequiredArgsConstructor
public class AuthApiExceptionHandler {

    private final JwtProperties jwtProperties;
    private final CookieUtil cookieUtil;

    @ExceptionHandler({UnauthorizedException.class, ForbiddenException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthException(
            BusinessException e, HttpServletResponse response) {

        deleteAuthCookies(response);

        return ResponseEntity.status(resolveStatus(e))
                .body(ApiResponse.error(ErrorResponse.of(e.getMessage())));
    }

    private int resolveStatus(BusinessException e) {
        if (e instanceof UnauthorizedException) {
            return HttpServletResponse.SC_UNAUTHORIZED;
        }
        if (e instanceof ForbiddenException) {
            return HttpServletResponse.SC_FORBIDDEN;
        }
        return HttpServletResponse.SC_BAD_REQUEST;
    }

    private void deleteAuthCookies(HttpServletResponse response) {
        boolean secure = jwtProperties.isCookieSecure();
        cookieUtil.deleteCookie(response, jwtProperties.getAccessCookieName(), secure);
        cookieUtil.deleteCookie(response, jwtProperties.getRefreshCookieName(), secure);
    }
}

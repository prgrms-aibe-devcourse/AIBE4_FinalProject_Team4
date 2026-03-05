package kr.java.documind.global.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.response.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint extends AbstractSecurityHandler
        implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private static final String LOGIN_PAGE = "/auth/login";

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {

        log.debug(
                "[JWT] 미인증: URI - {}, message - {}",
                request.getRequestURI(),
                authException.getMessage());

        if (isApiRequest(request)) {
            sendJsonResponse(response);
        } else {
            response.sendRedirect(LOGIN_PAGE);
        }
    }

    private void sendJsonResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> body = ApiResponse.error(ErrorResponse.of("로그인이 필요합니다."));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

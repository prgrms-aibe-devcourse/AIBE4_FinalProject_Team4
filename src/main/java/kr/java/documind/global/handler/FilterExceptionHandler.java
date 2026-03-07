package kr.java.documind.global.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.java.documind.global.exception.BusinessException;
import kr.java.documind.global.exception.TooManyRequestsException;
import kr.java.documind.global.security.filter.RateLimitFilter;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.response.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class FilterExceptionHandler extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (TooManyRequestsException e) {
            response.addHeader(RateLimitFilter.HEADER_RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
            response.addHeader(RateLimitFilter.HEADER_REMAINING_TOKEN, "0");
            sendErrorResponse(response, e.getStatus(), e.getMessage());
        } catch (BusinessException e) {
            sendErrorResponse(response, e.getStatus(), e.getMessage());
        } catch (Exception e) {
            log.error("필터 처리 중 예외가 발생했습니다: ", e);
            sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
        }
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.error(ErrorResponse.of(message));
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}

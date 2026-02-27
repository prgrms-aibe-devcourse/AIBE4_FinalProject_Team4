package kr.java.documind.global.handler;

import jakarta.servlet.http.HttpServletRequest;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * DispatcherServlet의 핸들러 결정(getHandler()) 이전에 발생하는 예외 처리
 * <p>
 * - checkMultipart()  : MaxUploadSizeExceededException (멀티파트 파싱 단계) - getHandler()      :
 * HttpRequestMethodNotSupportedException, NoResourceFoundException (핸들러 탐색 단계)
 * <p>
 * 이 시점에는 handler = null 이므로 어노테이션 제한이 있는 @ControllerAdvice는 적용되지 않는다. 따라서 제한 없는 별도 어드바이스로 분리하여
 * 처리한다.
 * <p>
 * Accept 헤더를 확인하여 JSON(API) 또는 HTML(View) 응답을 분기한다.
 */
@Slf4j
@ControllerAdvice
public class GlobalPreDispatchExceptionHandler {

    /**
     * 파일 크기 초과 (spring.servlet.multipart.max-file-size)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSizeExceeded(
        MaxUploadSizeExceededException e, HttpServletRequest request
    ) {
        log.warn("MaxUploadSizeExceededException [{}]: {}", request.getRequestURI(),
            e.getMessage());

        String message = "업로드 파일 크기가 허용 한도를 초과했습니다.";
        if (isJsonRequest(request)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(ErrorResponse.of(message)));
        }
        return errorView("error/error", HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    /**
     * 지원하지 않는 HTTP 메서드
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleHttpRequestMethodNotSupported(
        HttpRequestMethodNotSupportedException e, HttpServletRequest request
    ) {
        log.warn("HttpRequestMethodNotSupportedException [{}]: {}", request.getRequestURI(),
            e.getMethod());

        String message = "지원하지 않는 HTTP 메서드입니다: " + e.getMethod();
        if (isJsonRequest(request)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ErrorResponse.of(message)));
        }
        return errorView("error/error", HttpStatus.METHOD_NOT_ALLOWED, message);
    }

    /**
     * 등록되지 않은 URL (Spring Boot 3.2+)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("NoResourceFoundException [{}]: {}", request.getRequestURI(), e.getMessage());

        String message = "요청한 리소스를 찾을 수 없습니다.";
        if (isJsonRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorResponse.of(message)));
        }
        return errorView("error/404", HttpStatus.NOT_FOUND, message);
    }

    /**
     * Accept 헤더에 application/json이 포함되어 있으면 API 요청으로 판단
     */
    private boolean isJsonRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private ModelAndView errorView(String viewName, HttpStatus status, String message) {
        ModelAndView mav = new ModelAndView(viewName);
        mav.addObject("status", status.value());
        mav.addObject("message", message);
        mav.setStatus(status);
        return mav;
    }
}

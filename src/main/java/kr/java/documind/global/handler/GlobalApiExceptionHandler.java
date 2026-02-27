package kr.java.documind.global.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import kr.java.documind.global.exception.BusinessException;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class GlobalApiExceptionHandler {

    /**
     * @Valid, @Validated on @RequestBody
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
        MethodArgumentNotValidException e, HttpServletRequest request
    ) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> ErrorResponse.FieldError.of(fe.getField(), fe.getDefaultMessage()))
            .toList();

        log.warn("MethodArgumentNotValidException [{}]: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(
            ErrorResponse.withDetails("요청 값이 올바르지 않습니다.", fieldErrors)
        ));
    }

    /**
     * @Validated on @RequestParam, @PathVariable
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
        ConstraintViolationException e, HttpServletRequest request
    ) {
        List<ErrorResponse.FieldError> fieldErrors = e.getConstraintViolations().stream()
            .map(cv -> {
                String path = cv.getPropertyPath().toString();
                String field =
                    path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                return ErrorResponse.FieldError.of(field, cv.getMessage());
            })
            .toList();

        log.warn("ConstraintViolationException [{}]: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(
            ErrorResponse.withDetails("요청 값이 올바르지 않습니다.", fieldErrors)
        ));
    }

    /**
     * 잘못된 JSON 형식
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
        HttpMessageNotReadableException e, HttpServletRequest request
    ) {
        log.warn("HttpMessageNotReadableException [{}]: {}", request.getRequestURI(),
            e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse.error(ErrorResponse.of("요청 본문을 읽을 수 없습니다."))
        );
    }

    /**
     * 필수 @RequestParam 누락
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameter(
        MissingServletRequestParameterException e, HttpServletRequest request
    ) {
        log.warn("MissingServletRequestParameterException [{}]: {}", request.getRequestURI(),
            e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse.error(ErrorResponse.of("필수 요청 파라미터가 없습니다: " + e.getParameterName()))
        );
    }

    /**
     * @PathVariable, @RequestParam 타입 불일치
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
        MethodArgumentTypeMismatchException e, HttpServletRequest request
    ) {
        log.warn("MethodArgumentTypeMismatchException [{}]: {}", request.getRequestURI(),
            e.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse.error(ErrorResponse.of("요청 파라미터 타입이 올바르지 않습니다: " + e.getName()))
        );
    }

    /**
     * 비즈니스 예외 (BusinessException 및 하위 예외) getDetails()가 있으면 details 포함, 없으면 message만
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
        BusinessException e, HttpServletRequest request
    ) {
        log.warn("BusinessException [{}]: {}", request.getRequestURI(), e.getMessage());
        ErrorResponse errorResponse = e.getDetails() != null
            ? ErrorResponse.withDetails(e.getMessage(), e.getDetails())
            : ErrorResponse.of(e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(errorResponse));
    }

    /**
     * 그 외 모든 예외 (500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e,
        HttpServletRequest request) {
        log.error("Unhandled exception [{}]", request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponse.error(ErrorResponse.of("서버 오류가 발생했습니다."))
        );
    }
}

package kr.java.documind.global.exception;

import org.springframework.http.HttpStatus;

/**
 * Redis 다운, DB 점검 등 일시적인 인프라 마비 시 발생하는 예외
 */
public class ServiceUnavailableException extends BusinessException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}

package kr.java.documind.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TooManyRequestsException extends BusinessException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = 0;
    }

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}

package kr.java.documind.global.exception;

import org.springframework.http.HttpStatus;

/** 서버 내부 로직 또는 데이터 정합성 오류 시 발생하는 예외 */
public class InternalServerException extends BusinessException {

    public InternalServerException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public InternalServerException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}

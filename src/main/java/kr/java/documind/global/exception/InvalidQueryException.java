package kr.java.documind.global.exception;

import org.springframework.http.HttpStatus;

/** 허용되지 않는 컬럼, 연산자, 형식 등 쿼리 구성 오류. */
public class InvalidQueryException extends BusinessException {

    public InvalidQueryException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}

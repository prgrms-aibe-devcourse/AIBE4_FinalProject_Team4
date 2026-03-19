package kr.java.documind.global.exception;

import org.springframework.http.HttpStatus;

/** 쿼리 복잡도 제한 초과 (시간 범위 90일 초과 등). */
public class QueryComplexityException extends BusinessException {

    public QueryComplexityException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}

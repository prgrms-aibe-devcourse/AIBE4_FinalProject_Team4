package kr.java.documind.domain.auth.exception;

import kr.java.documind.global.exception.UnauthorizedException;

public class InvalidTokenException extends UnauthorizedException {
    public InvalidTokenException() {
        super("유효하지 않은 토큰입니다.");
    }
}

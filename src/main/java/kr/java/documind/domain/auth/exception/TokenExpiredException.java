package kr.java.documind.domain.auth.exception;

import kr.java.documind.global.exception.UnauthorizedException;

public class TokenExpiredException extends UnauthorizedException {

    public TokenExpiredException() {
        super("토큰이 만료되었습니다.");
    }
}

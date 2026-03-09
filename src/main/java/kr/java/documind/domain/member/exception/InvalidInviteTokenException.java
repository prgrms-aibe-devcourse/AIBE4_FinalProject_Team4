package kr.java.documind.domain.member.exception;

import kr.java.documind.global.exception.BadRequestException;

public class InvalidInviteTokenException extends BadRequestException {

    public InvalidInviteTokenException(String message) {
        super(message);
    }
}

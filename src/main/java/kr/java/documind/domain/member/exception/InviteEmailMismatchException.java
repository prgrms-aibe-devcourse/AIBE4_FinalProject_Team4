package kr.java.documind.domain.member.exception;

import kr.java.documind.global.exception.ForbiddenException;

public class InviteEmailMismatchException extends ForbiddenException {

    private final String expectedEmail;

    public InviteEmailMismatchException(String expectedEmail) {
        super(expectedEmail + " 계정으로 로그인해야 초대를 수락할 수 있습니다.");
        this.expectedEmail = expectedEmail;
    }

    public String getExpectedEmail() {
        return expectedEmail;
    }
}

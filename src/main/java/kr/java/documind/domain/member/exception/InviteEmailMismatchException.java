package kr.java.documind.domain.member.exception;

import kr.java.documind.global.exception.ForbiddenException;

public class InviteEmailMismatchException extends ForbiddenException {

    private final String expectedEmail;

    public InviteEmailMismatchException(String expectedEmail) {
        super("이 초대 링크를 사용할 권한이 없습니다.");
        this.expectedEmail = expectedEmail;
    }

    public String getExpectedEmail() {
        return expectedEmail;
    }
}

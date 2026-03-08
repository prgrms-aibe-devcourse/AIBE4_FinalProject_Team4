package kr.java.documind.domain.member.exception;

import kr.java.documind.global.exception.NotFoundException;

public class CompanyNotFoundException extends NotFoundException {

    public CompanyNotFoundException() {
        super("회사 정보를 찾을 수 없습니다.");
    }
}

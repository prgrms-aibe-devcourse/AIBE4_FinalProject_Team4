package kr.java.documind.domain.member.exception;

import kr.java.documind.global.exception.NotFoundException;

public class DeletedProjectException extends NotFoundException {

    public DeletedProjectException() {
        super("삭제된 프로젝트입니다.");
    }
}

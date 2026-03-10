package kr.java.documind.domain.auth.exception;

import kr.java.documind.global.exception.NotFoundException;

public class DeletedProjectException extends NotFoundException {

    public DeletedProjectException() {
        super("삭제된 프로젝트입니다.");
    }
}

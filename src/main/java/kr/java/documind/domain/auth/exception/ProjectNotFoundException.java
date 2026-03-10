package kr.java.documind.domain.auth.exception;

import kr.java.documind.global.exception.NotFoundException;

public class ProjectNotFoundException extends NotFoundException {

    public ProjectNotFoundException() {
        super("프로젝트를 찾을 수 없습니다.");
    }
}

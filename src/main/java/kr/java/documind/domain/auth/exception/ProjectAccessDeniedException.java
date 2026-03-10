package kr.java.documind.domain.auth.exception;

import kr.java.documind.global.exception.ForbiddenException;

public class ProjectAccessDeniedException extends ForbiddenException {

    public ProjectAccessDeniedException() {
        super("프로젝트에 접근 권한이 없습니다.");
    }
}

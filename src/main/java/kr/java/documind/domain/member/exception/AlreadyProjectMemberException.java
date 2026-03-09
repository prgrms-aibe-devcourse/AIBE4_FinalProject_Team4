package kr.java.documind.domain.member.exception;

import kr.java.documind.global.exception.ConflictException;

public class AlreadyProjectMemberException extends ConflictException {

    private final String projectPublicId;

    public AlreadyProjectMemberException(String projectPublicId) {
        super("이미 해당 프로젝트의 멤버입니다.");
        this.projectPublicId = projectPublicId;
    }

    public String getProjectPublicId() {
        return projectPublicId;
    }
}

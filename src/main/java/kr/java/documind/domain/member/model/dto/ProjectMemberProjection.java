package kr.java.documind.domain.member.model.dto;

import java.util.UUID;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.enums.ProjectRole;
import kr.java.documind.domain.member.model.enums.ProjectStatus;

public interface ProjectMemberProjection {

    UUID getProjectId();

    String getPublicId();

    String getProjectName();

    /** 프로젝트 상태 (ACTIVE / SUSPENDED / DELETED). 삭제 여부 판별에 사용한다. */
    ProjectStatus getProjectStatus();

    Long getProjectMemberId();

    ProjectRole getProjectRole();

    AccountStatus getMemberStatus();
}

package kr.java.documind.domain.auth.model.dto;

import java.util.UUID;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.auth.model.enums.ProjectRole;
import kr.java.documind.domain.member.model.enums.ProjectStatus;

public interface ProjectMemberProjection {

    UUID getProjectId();

    String getPublicId();

    String getProjectName();

    ProjectStatus getProjectStatus();

    Long getProjectMemberId();

    ProjectRole getProjectRole();

    AccountStatus getMemberStatus();
}

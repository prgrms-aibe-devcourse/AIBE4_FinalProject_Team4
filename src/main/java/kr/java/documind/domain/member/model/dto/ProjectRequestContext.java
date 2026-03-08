package kr.java.documind.domain.member.model.dto;

import java.util.UUID;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.enums.ProjectRole;
import kr.java.documind.domain.member.model.enums.ProjectStatus;

public record ProjectRequestContext(
        UUID projectId,
        String publicId,
        String projectName,
        ProjectStatus projectStatus,
        UUID actorMemberId,
        Long projectMemberId,
        ProjectRole projectRole,
        boolean isProjectMember,
        boolean isProjectManager) {

    /** 프로젝트가 소프트 딜리트된 상태인지 여부 */
    public boolean isProjectDeleted() {
        return projectStatus == ProjectStatus.DELETED;
    }

    public static ProjectRequestContext from(
            ProjectMemberProjection projection, UUID actorMemberId) {

        boolean activeMember = projection.getMemberStatus() == AccountStatus.ACTIVE;
        boolean manager = activeMember && projection.getProjectRole() == ProjectRole.MANAGER;

        return new ProjectRequestContext(
                projection.getProjectId(),
                projection.getPublicId(),
                projection.getProjectName(),
                projection.getProjectStatus(),
                actorMemberId,
                projection.getProjectMemberId(),
                projection.getProjectRole(),
                activeMember,
                manager);
    }
}

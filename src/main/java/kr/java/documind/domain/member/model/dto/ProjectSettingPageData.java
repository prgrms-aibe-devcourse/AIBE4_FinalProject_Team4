package kr.java.documind.domain.member.model.dto;

import java.util.List;
import kr.java.documind.domain.auth.model.dto.HeaderInfo;
import kr.java.documind.domain.auth.model.enums.ProjectRole;

public record ProjectSettingPageData(
        HeaderInfo headerInfo,
        ProjectSummary project,
        ProjectRole currentRole,
        boolean isCeo,
        List<ProjectMemberRow> members,
        List<ProjectSummary> myProjects,
        ProjectApiKeyInfo apiKeyInfo) {

    public boolean isManager() {
        return currentRole == ProjectRole.MANAGER;
    }

    public boolean isMember() {
        return currentRole == ProjectRole.MEMBER;
    }
}

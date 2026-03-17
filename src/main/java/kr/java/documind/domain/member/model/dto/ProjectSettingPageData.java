package kr.java.documind.domain.member.model.dto;

import java.util.List;
import kr.java.documind.domain.auth.model.enums.ProjectRole;

public record ProjectSettingPageData(
        ProjectSummary project,
        ProjectRole currentRole,
        boolean isCeo,
        List<ProjectMemberRow> members,
        ProjectApiKeyInfo apiKeyInfo) {

    public boolean isManager() {
        return currentRole == ProjectRole.MANAGER;
    }

    public boolean isMember() {
        return currentRole == ProjectRole.MEMBER;
    }
}

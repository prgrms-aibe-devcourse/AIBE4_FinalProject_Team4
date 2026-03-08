package kr.java.documind.domain.member.model.dto;

import java.util.List;
import kr.java.documind.domain.member.model.enums.ProjectRole;

public record ProjectSettingPageData(
        HeaderInfo headerInfo,
        ProjectDetail project,
        ProjectRole currentRole,
        boolean isCeo,
        List<ProjectMemberRow> members,
        List<ProjectSummary> myProjects,
        ProjectApiKeyInfo apiKeyInfo) {

    /** 현재 회원이 프로젝트 관리자(MANAGER) 인지 여부 */
    public boolean manager() {
        return currentRole == ProjectRole.MANAGER;
    }

    /** 현재 회원이 프로젝트 구성원(MEMBER) 인지 여부 */
    public boolean member() {
        return currentRole == ProjectRole.MEMBER;
    }
}

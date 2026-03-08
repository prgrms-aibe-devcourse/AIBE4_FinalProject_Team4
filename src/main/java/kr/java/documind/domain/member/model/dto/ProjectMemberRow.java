package kr.java.documind.domain.member.model.dto;

import java.util.UUID;
import kr.java.documind.domain.member.model.enums.ProjectRole;

public record ProjectMemberRow(
        UUID memberId,
        String name,
        String nickname,
        String email,
        String profileUrl,
        ProjectRole role,
        boolean isCurrentUser,
        boolean isCeo) {

    public boolean manager() {
        return role == ProjectRole.MANAGER;
    }
}

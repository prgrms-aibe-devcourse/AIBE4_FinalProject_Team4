package kr.java.documind.domain.member.model.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import kr.java.documind.domain.member.model.enums.ProjectRole;

public record InviteViewData(
        String rawToken,
        String projectName,
        String projectPublicId,
        String inviterName,
        ProjectRole targetRole,
        boolean hasDifferentCompany,
        boolean isCeo,
        String currentCompanyName,
        LocalDateTime expiresAt) {

    public long expiresAtEpochMs() {
        return expiresAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

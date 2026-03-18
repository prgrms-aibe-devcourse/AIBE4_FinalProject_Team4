package kr.java.documind.domain.member.model.dto;

import java.time.OffsetDateTime;
import kr.java.documind.domain.auth.model.enums.ProjectRole;

public record InviteViewData(
        String rawToken,
        String projectName,
        String projectPublicId,
        String inviterName,
        ProjectRole targetRole,
        boolean hasDifferentCompany,
        boolean isCeo,
        String currentCompanyName,
        OffsetDateTime expiresAt) {

    public long expiresAtEpochMs() {
        return expiresAt.toInstant().toEpochMilli();
    }
}

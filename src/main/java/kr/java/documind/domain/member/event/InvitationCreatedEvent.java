package kr.java.documind.domain.member.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationCreatedEvent(
        UUID invitationId,
        String inviterName,
        String projectName,
        String targetEmail,
        String rawToken,
        OffsetDateTime expiresAt) {}

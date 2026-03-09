package kr.java.documind.domain.member.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record InvitationCreatedEvent(
        UUID invitationId,
        String inviterName,
        String projectName,
        String targetEmail,
        String rawToken,
        LocalDateTime expiresAt) {}

package kr.java.documind.domain.patchnote.event;

import java.time.Instant;
import java.util.UUID;
import kr.java.documind.domain.issue.model.enums.IssueStatus;

public record IssueStatusChangedEvent(
        Long issueId,
        UUID projectId,
        IssueStatus oldStatus,
        IssueStatus newStatus,
        boolean excludeFromPatchNote,
        UUID actorId,
        Instant occurredAt) {}

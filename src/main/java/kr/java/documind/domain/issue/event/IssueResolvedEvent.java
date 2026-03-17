package kr.java.documind.domain.issue.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IssueResolvedEvent(
        Long issueId,
        UUID projectId,
        String title,
        String description,
        String fingerprint,
        OffsetDateTime resolvedAt,
        UUID resolvedBy) {}

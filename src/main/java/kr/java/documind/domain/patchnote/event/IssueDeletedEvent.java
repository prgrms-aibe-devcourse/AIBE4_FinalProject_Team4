package kr.java.documind.domain.patchnote.event;

import java.time.Instant;
import java.util.UUID;

public record IssueDeletedEvent(Long issueId, UUID projectId, UUID actorId, Instant occurredAt) {}

package kr.java.documind.domain.patchnote.event;

import java.time.Instant;
import java.util.UUID;

public record DocumentEmbeddedEvent(
        Long sourceId,
        UUID projectId,
        String documentName,
        String documentGroupName,
        String category,
        boolean isNewDocument,
        Instant occurredAt) {}

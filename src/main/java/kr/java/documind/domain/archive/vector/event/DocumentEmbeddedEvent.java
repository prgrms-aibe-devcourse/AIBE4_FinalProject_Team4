package kr.java.documind.domain.archive.vector.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentEmbeddedEvent(
        Long sourceId,
        UUID projectId,
        Long documentGroupId,
        String documentName,
        String documentGroupName,
        String category,
        boolean isNewDocument,
        boolean excludeFromPatchNote,
        OffsetDateTime sourceCreatedAt) {}

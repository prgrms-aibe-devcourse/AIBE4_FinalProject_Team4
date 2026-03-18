package kr.java.documind.domain.patchnote.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentEmbeddedEvent(
        Long sourceId,
        UUID projectId,
        String documentName,
        String documentGroupName,
        String category,
        boolean isNewDocument,
        boolean excludeFromPatchNote,
        OffsetDateTime sourceCreatedAt) {}

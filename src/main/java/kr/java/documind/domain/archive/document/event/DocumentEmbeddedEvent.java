package kr.java.documind.domain.archive.document.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentEmbeddedEvent(
        Long sourceId,
        UUID projectId,
        String publicId,
        UUID memberId,
        Long documentGroupId,
        String documentName,
        String documentGroupName,
        String category,
        boolean isNewDocument,
        boolean excludeFromPatchNote,
        OffsetDateTime sourceCreatedAt) {}

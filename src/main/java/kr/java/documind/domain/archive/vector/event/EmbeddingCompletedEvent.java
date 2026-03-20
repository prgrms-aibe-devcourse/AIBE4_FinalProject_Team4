package kr.java.documind.domain.archive.vector.event;

import java.util.UUID;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;

public record EmbeddingCompletedEvent(
        UUID projectId,
        UUID memberId,
        Long sourceId,
        EmbeddingStatus status,
        boolean excludeFromPatchNote) {}

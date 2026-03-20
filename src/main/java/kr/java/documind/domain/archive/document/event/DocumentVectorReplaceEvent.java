package kr.java.documind.domain.archive.document.event;

import java.util.UUID;

public record DocumentVectorReplaceEvent(
        String publicId,
        UUID projectId,
        UUID memberId,
        Long sourceId,
        String storedKey,
        boolean excludeFromPatchNote) {}

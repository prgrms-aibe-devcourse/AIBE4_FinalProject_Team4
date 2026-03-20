package kr.java.documind.domain.archive.document.event;

import java.util.UUID;

public record DocumentVectorCreateEvent(
        String publicId,
        UUID projectId,
        UUID memberId,
        Long sourceId,
        String storedKey,
        boolean excludeFromPatchNote) {}

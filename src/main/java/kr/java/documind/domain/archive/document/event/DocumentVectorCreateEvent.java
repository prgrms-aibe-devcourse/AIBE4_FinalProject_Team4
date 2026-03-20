package kr.java.documind.domain.archive.document.event;

import java.util.UUID;

public record DocumentVectorCreateEvent(
        UUID projectId,
        UUID memberId,
        Long sourceId,
        String storedKey,
        boolean excludeFromPatchNote) {}

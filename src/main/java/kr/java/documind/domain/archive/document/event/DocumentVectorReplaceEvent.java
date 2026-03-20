package kr.java.documind.domain.archive.document.event;

import java.util.UUID;

public record DocumentVectorReplaceEvent(
        UUID projectId, Long sourceId, String storedKey, boolean excludeFromPatchNote) {}

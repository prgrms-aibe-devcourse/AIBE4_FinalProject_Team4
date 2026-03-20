package kr.java.documind.domain.archive.document.event;

import java.util.UUID;

public record DocumentVectorDeleteEvent(UUID projectId, Long sourceId) {}

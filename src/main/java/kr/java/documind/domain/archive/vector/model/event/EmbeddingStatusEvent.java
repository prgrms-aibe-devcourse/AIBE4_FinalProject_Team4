package kr.java.documind.domain.archive.vector.model.event;

import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;

public record EmbeddingStatusEvent(Long sourceId, EmbeddingStatus status) {}

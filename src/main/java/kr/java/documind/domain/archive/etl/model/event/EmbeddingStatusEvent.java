package kr.java.documind.domain.archive.etl.model.event;

import kr.java.documind.domain.archive.etl.model.enums.EmbeddingStatus;

public record EmbeddingStatusEvent(Long sourceId, EmbeddingStatus status) {}

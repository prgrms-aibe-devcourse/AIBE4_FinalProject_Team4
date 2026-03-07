package kr.java.documind.domain.archive.document.model.event;

import java.nio.file.Path;

public record DocumentEtlEvent(Long sourceId, Path tempFilePath) {}

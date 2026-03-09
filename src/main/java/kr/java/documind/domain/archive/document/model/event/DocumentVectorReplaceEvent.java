package kr.java.documind.domain.archive.document.model.event;

import java.nio.file.Path;

public record DocumentVectorReplaceEvent(Long sourceId, Path tempFilePath) {}

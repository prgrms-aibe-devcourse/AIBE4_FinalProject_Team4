package kr.java.documind.domain.archive.document.model.event;

import java.nio.file.Path;

public record DocumentVectorCreateEvent(Long sourceId, Path tempFilePath) {}

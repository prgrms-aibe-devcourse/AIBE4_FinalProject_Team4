package kr.java.documind.domain.archive.document.model.event;

import java.nio.file.Path;
import java.util.UUID;

public record DocumentVectorCreateEvent(UUID projectId, Long sourceId, Path tempFilePath) {}

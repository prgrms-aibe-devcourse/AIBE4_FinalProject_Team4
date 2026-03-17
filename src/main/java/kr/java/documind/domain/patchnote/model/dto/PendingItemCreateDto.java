package kr.java.documind.domain.patchnote.model.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.global.enums.SourceType;

public record PendingItemCreateDto(
        UUID projectId,
        Long sourceId,
        SourceType sourceType,
        String title,
        String summary,
        String choseong,
        PatchType patchType,
        PendingItemStatus status,
        OffsetDateTime sourceCreatedAt) {}

package kr.java.documind.domain.patchnote.model.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.global.enums.SourceType;

/**
 * Pending Item 생성/갱신 요청 DTO.
 *
 * @param changeIndex 동일 소스 내 변경 순번 (ISSUE = 0 고정, DOCUMENT diff = candidate.chunkIndex)
 * @param evidence RAG 컨텍스트 삽입용 이전↔현재 텍스트 요약 (diff 기반 항목만 non-null)
 * @param score 패치노트 적합도 점수 (diff 기반 항목만 non-null)
 */
public record PendingItemCreateRequest(
        UUID projectId,
        Long sourceId,
        SourceType sourceType,
        String title,
        String summary,
        String choseong,
        PatchType patchType,
        PendingItemStatus status,
        OffsetDateTime sourceCreatedAt,
        int changeIndex,
        String evidence,
        Double score) {}

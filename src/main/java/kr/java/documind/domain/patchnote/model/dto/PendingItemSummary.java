package kr.java.documind.domain.patchnote.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.global.enums.SourceType;

/** Pending Item 목록 피드 응답 DTO. */
@Schema(description = "패치노트 피드 항목")
public record PendingItemSummary(
        @Schema(description = "항목 ID", example = "42") Long id,
        @Schema(description = "원본 소스 ID (이슈 ID 또는 문서 ID)", example = "7") Long sourceId,
        @Schema(description = "소스 타입", example = "DOCUMENT") SourceType sourceType,
        @Schema(description = "LLM이 생성한 패치노트용 제목", example = "몬스터 밸런스 패치 상세") String title,
        @Schema(description = "LLM이 생성한 요약", example = "몬스터 체력·공격력 수치가 조정되었습니다.")
                String summary,
        @Schema(description = "패치 분류", example = "CHANGE") PatchType patchType,
        @Schema(description = "항목 상태", example = "PENDING") PendingItemStatus status,
        @Schema(description = "원본 소스가 삭제되었으면 true", example = "false") boolean sourceDeleted,
        @Schema(
                        description = "원본 소스 생성 일시 (ISO 8601)",
                        example = "2025-06-01T09:00:00Z")
                OffsetDateTime sourceCreatedAt) {

    public static PendingItemSummary from(PendingItem item) {
        return new PendingItemSummary(
                item.getId(),
                item.getSourceId(),
                item.getSourceType(),
                item.getTitle(),
                item.getSummary(),
                item.getPatchType(),
                item.getStatus(),
                item.isSourceDeleted(),
                item.getSourceCreatedAt());
    }
}

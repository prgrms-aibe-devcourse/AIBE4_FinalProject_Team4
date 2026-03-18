package kr.java.documind.domain.patchnote.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import kr.java.documind.domain.patchnote.model.entity.PatchNote;
import kr.java.documind.domain.patchnote.model.enums.PatchNoteStatus;

/**
 * 패치노트 목록 조회용 요약 DTO.
 *
 * @param id           패치노트 ID
 * @param title        제목
 * @param versionLabel 버전 레이블 (예: "v1.2.0")
 * @param status       상태 (DRAFT / PUBLISHED / DELETED)
 * @param createdAt    생성일시
 */
@Schema(description = "패치노트 목록 항목")
public record PatchNoteSummary(
        @Schema(description = "패치노트 ID", example = "1") Long id,
        @Schema(description = "제목", example = "v1.2.0 업데이트") String title,
        @Schema(description = "버전 레이블", example = "v1.2.0") String versionLabel,
        @Schema(description = "상태") PatchNoteStatus status,
        @Schema(description = "생성일시") OffsetDateTime createdAt) {

    public static PatchNoteSummary from(PatchNote patchNote) {
        return new PatchNoteSummary(
                patchNote.getId(),
                patchNote.getTitle(),
                patchNote.getVersionLabel(),
                patchNote.getStatus(),
                patchNote.getCreatedAt());
    }
}

package kr.java.documind.domain.patchnote.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import kr.java.documind.domain.patchnote.model.entity.PatchNote;
import kr.java.documind.domain.patchnote.model.enums.PatchNoteStatus;

/**
 * 패치노트 단건 조회 DTO.
 *
 * <p>본문({@code content})은 source 태그가 제거된 정제 컨텐츠만 포함한다.
 *
 * @param id 패치노트 ID
 * @param title 제목
 * @param content 본문 (Markdown)
 * @param versionLabel 버전 레이블 (예: "v1.2.0")
 * @param majorVersion major 버전
 * @param minorVersion minor 버전
 * @param patchVersion patch 버전
 * @param status 상태
 * @param createdAt 생성일시
 */
@Schema(description = "패치노트 상세")
public record PatchNoteDetail(
        @Schema(description = "패치노트 ID", example = "1") Long id,
        @Schema(description = "제목", example = "v1.2.0 업데이트") String title,
        @Schema(description = "본문 (Markdown)") String content,
        @Schema(description = "버전 레이블", example = "v1.2.0") String versionLabel,
        @Schema(description = "major 버전", example = "1") int majorVersion,
        @Schema(description = "minor 버전", example = "2") int minorVersion,
        @Schema(description = "patch 버전", example = "0") int patchVersion,
        @Schema(description = "상태") PatchNoteStatus status,
        @Schema(description = "생성일시") OffsetDateTime createdAt) {

    public static PatchNoteDetail from(PatchNote patchNote) {
        return new PatchNoteDetail(
                patchNote.getId(),
                patchNote.getTitle(),
                patchNote.getContent(),
                patchNote.getVersionLabel(),
                patchNote.getMajorVersion(),
                patchNote.getMinorVersion(),
                patchNote.getPatchVersion(),
                patchNote.getStatus(),
                patchNote.getCreatedAt());
    }
}

package kr.java.documind.domain.patchnote.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 패치노트 저장 요청 DTO.
 *
 * <p>SSE 스트리밍 완료 후 프론트엔드가 {@code done} 이벤트의 {@code cleanedContent}를 그대로 담아 전송한다. 선택된 pending_item
 * ID 목록을 함께 전달하면 해당 항목이 COMPLETED 처리된다.
 *
 * @param title 패치노트 제목
 * @param content 패치노트 본문 (source 태그 제거된 정제 컨텐츠)
 * @param majorVersion major 버전
 * @param minorVersion minor 버전
 * @param patchVersion patch 버전
 * @param itemIds COMPLETED 처리할 PendingItem ID 목록 (빈 목록 허용)
 */
@Schema(description = "패치노트 저장 요청")
public record PatchNoteCreateRequest(
        @Schema(description = "패치노트 제목", example = "v1.2.0 업데이트")
                @NotBlank(message = "제목은 필수입니다.")
                @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
                String title,
        @Schema(description = "패치노트 본문 (cleanedContent)", example = "## 수정\n- 결제 오류가 수정되었습니다.")
                @NotBlank(message = "본문 내용은 필수입니다.")
                String content,
        @Schema(description = "major 버전", example = "1") @Min(0) int majorVersion,
        @Schema(description = "minor 버전", example = "2") @Min(0) int minorVersion,
        @Schema(description = "patch 버전", example = "0") @Min(0) int patchVersion,
        @Schema(description = "COMPLETED 처리할 PendingItem ID 목록", example = "[1, 2, 3]") @NotNull
                List<Long> itemIds,
        @Schema(description = "동일 버전이 존재할 때 기존 초안을 삭제하고 덮어쓸지 여부", example = "false")
                boolean overwrite) {}

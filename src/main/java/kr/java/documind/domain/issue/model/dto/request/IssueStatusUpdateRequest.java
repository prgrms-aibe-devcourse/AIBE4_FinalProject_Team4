package kr.java.documind.domain.issue.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import kr.java.documind.domain.issue.model.enums.IssueStatus;

/**
 * 이슈 상태 변경 요청
 *
 * <p>PUT /api/issues/{issueId}/status
 */
public record IssueStatusUpdateRequest(
        @Schema(
                        description = "변경할 상태",
                        example = "IN_PROGRESS",
                        allowableValues = {"TODO", "IN_PROGRESS", "RESOLVED"})
                @NotNull(message = "상태는 필수입니다")
                IssueStatus status,
        @Schema(
                        description = "패치노트 반영 여부 (RESOLVED 상태로 변경 시에만 사용)",
                        example = "true",
                        defaultValue = "true")
                Boolean includeInPatchNote) {

    /**
     * 패치노트 반영 여부 (기본값: true)
     *
     * @return RESOLVED 상태가 아니거나 null인 경우 true 반환
     */
    public boolean shouldIncludeInPatchNote() {
        return includeInPatchNote == null || includeInPatchNote;
    }
}

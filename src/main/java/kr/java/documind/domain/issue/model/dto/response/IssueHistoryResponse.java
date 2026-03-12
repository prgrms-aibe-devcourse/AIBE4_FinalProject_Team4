package kr.java.documind.domain.issue.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.IssueHistory;

/**
 * 이슈 변경 이력 응답
 *
 * <p>GET /api/issues/{issueId}/histories
 */
public record IssueHistoryResponse(
        @Schema(description = "이력 ID", example = "1") Long id,
        @Schema(description = "이슈 ID", example = "101") Long issueId,
        @Schema(description = "변경자 멤버 ID", example = "123e4567-e89b-12d3-a456-426614174000")
                UUID modifierId,
        @Schema(
                        description = "변경된 필드명",
                        example = "STATUS",
                        allowableValues = {"STATUS", "ASSIGNEE", "PRIORITY"})
                String fieldName,
        @Schema(description = "변경 전 값", example = "TODO") String beforeValue,
        @Schema(description = "변경 후 값", example = "IN_PROGRESS") String afterValue,
        @Schema(description = "생성 시각", example = "2024-03-11T10:30:00Z") OffsetDateTime createdAt) {

    /**
     * Entity → DTO 변환
     *
     * @param history IssueHistory 엔티티
     * @return IssueHistoryResponse
     */
    public static IssueHistoryResponse from(IssueHistory history) {
        return new IssueHistoryResponse(
                history.getId(),
                history.getIssueId(),
                history.getModifierId(),
                history.getFieldName(),
                history.getBeforeValue(),
                history.getAfterValue(),
                history.getCreatedAt());
    }
}

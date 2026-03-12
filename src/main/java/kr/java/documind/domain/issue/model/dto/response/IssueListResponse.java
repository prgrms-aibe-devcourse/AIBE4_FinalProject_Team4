package kr.java.documind.domain.issue.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.enums.IssueType;

/**
 * 이슈 목록 조회 응답 (요약 정보)
 *
 * <p>GET /api/projects/{projectId}/issues
 */
public record IssueListResponse(
        @Schema(description = "이슈 ID", example = "101") Long id,
        @Schema(description = "이슈 제목", example = "NullPointerException in GameController")
                String title,
        @Schema(description = "이슈 유형", example = "BUG") IssueType issueType,
        @Schema(description = "에러 타입", example = "NULL_POINTER") ErrorType errorType,
        @Schema(description = "상태", example = "TODO") IssueStatus status,
        @Schema(description = "심각도", example = "HIGH") IssueSeverity severity,
        @Schema(description = "심각도 점수", example = "85") Integer severityScore,
        @Schema(description = "담당자 ID", example = "123e4567-e89b-12d3-a456-426614174000")
                UUID assigneeId,
        @Schema(description = "발생 횟수", example = "42") Integer occurrenceCount,
        @Schema(description = "최초 발생 시각", example = "2024-03-11T10:00:00Z")
                OffsetDateTime firstOccurredAt,
        @Schema(description = "최근 발생 시각", example = "2024-03-11T15:30:00Z")
                OffsetDateTime lastOccurredAt,
        @Schema(description = "생성 시각", example = "2024-03-11T10:00:00Z") OffsetDateTime createdAt,
        @Schema(description = "수정 시각", example = "2024-03-11T15:30:00Z") OffsetDateTime updatedAt) {

    /**
     * Entity → DTO 변환
     *
     * @param issue Issue 엔티티
     * @return IssueListResponse
     */
    public static IssueListResponse from(Issue issue) {
        return new IssueListResponse(
                issue.getId(),
                issue.getTitle(),
                issue.getIssueType(),
                issue.getErrorType(),
                issue.getStatus(),
                issue.getSeverity(),
                issue.getSeverityScore(),
                issue.getAssigneeId(),
                issue.getOccurrenceCount(),
                issue.getFirstOccurredAt(),
                issue.getLastOccurredAt(),
                issue.getCreatedAt(),
                issue.getUpdatedAt());
    }
}

package kr.java.documind.domain.issue.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.FingerprintQuality;
import kr.java.documind.domain.issue.model.enums.IssuePriority;
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
        @Schema(description = "우선순위", example = "P2") IssuePriority priority,
        @Schema(description = "심각도", example = "HIGH") IssueSeverity severity,
        @Schema(description = "심각도 점수", example = "85") Integer severityScore,
        @Schema(description = "Fingerprint 품질", example = "HIGH")
                FingerprintQuality fingerprintQuality,
        @Schema(description = "담당자 ID") UUID assigneeId,
        @Schema(description = "담당자 정보") AssigneeInfo assignee,
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
                issue.getPriority(),
                issue.getSeverity(),
                issue.getSeverityScore(),
                inferFingerprintQuality(issue),
                issue.getAssigneeId(),
                null,
                issue.getOccurrenceCount(),
                issue.getFirstOccurredAt(),
                issue.getLastOccurredAt(),
                issue.getCreatedAt(),
                issue.getUpdatedAt());
    }

    /**
     * Entity → DTO 변환 (담당자 정보 포함)
     *
     * @param issue Issue 엔티티
     * @param assignee 담당자 정보
     * @return IssueListResponse
     */
    public static IssueListResponse from(Issue issue, AssigneeInfo assignee) {
        return new IssueListResponse(
                issue.getId(),
                issue.getTitle(),
                issue.getIssueType(),
                issue.getErrorType(),
                issue.getStatus(),
                issue.getPriority(),
                issue.getSeverity(),
                issue.getSeverityScore(),
                inferFingerprintQuality(issue),
                issue.getAssigneeId(),
                assignee,
                issue.getOccurrenceCount(),
                issue.getFirstOccurredAt(),
                issue.getLastOccurredAt(),
                issue.getCreatedAt(),
                issue.getUpdatedAt());
    }

    /**
     * stackKey와 errorType으로 Fingerprint 품질 추론
     *
     * <p>정확한 품질이 아닌 추론값이므로 참고용으로만 사용
     *
     * @param issue Issue 엔티티
     * @return 추론된 FingerprintQuality
     */
    private static FingerprintQuality inferFingerprintQuality(Issue issue) {
        String stackKey = issue.getStackKey();
        ErrorType errorType = issue.getErrorType();

        // stackKey가 있고 라인번호(:숫자) 포함 → HIGH (정확한 위치)
        if (stackKey != null && !stackKey.isBlank() && stackKey.matches(".*:\\d+$")) {
            return FingerprintQuality.HIGH;
        }

        // stackKey가 있지만 라인번호 없음 → MEDIUM (부분 정보)
        if (stackKey != null && !stackKey.isBlank()) {
            return FingerprintQuality.MEDIUM;
        }

        // stackKey가 없고 errorType이 명확하면 LOW (예외 타입만)
        if (errorType != null && errorType != ErrorType.UNKNOWN) {
            return FingerprintQuality.LOW;
        }

        // errorType도 UNKNOWN이면 VERY_LOW (최소 정보)
        return FingerprintQuality.VERY_LOW;
    }
}

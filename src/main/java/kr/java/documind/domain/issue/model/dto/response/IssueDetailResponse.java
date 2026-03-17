package kr.java.documind.domain.issue.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssuePriority;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.enums.IssueType;

/**
 * 이슈 상세 조회 응답 (전체 정보)
 *
 * <p>GET /api/projects/{projectId}/issues/{issueId}
 */
public record IssueDetailResponse(
        @Schema(description = "이슈 ID", example = "101") Long id,
        @Schema(description = "담당자 정보") AssigneeInfo assignee,
        @Schema(description = "프로젝트 ID", example = "123e4567-e89b-12d3-a456-426614174001")
                UUID projectId,
        @Schema(description = "이슈 제목", example = "NullPointerException in GameController")
                String title,
        @Schema(description = "이슈 설명", example = "게임 컨트롤러에서 NPE 발생") String description,
        @Schema(description = "핑거프린트", example = "a1b2c3d4e5f6...") String fingerprint,
        @Schema(description = "이슈 유형", example = "BUG") IssueType issueType,
        @Schema(description = "상태", example = "IN_PROGRESS") IssueStatus status,
        @Schema(description = "우선순위", example = "P2") IssuePriority priority,
        @Schema(description = "심각도", example = "HIGH") IssueSeverity severity,
        @Schema(description = "심각도 점수", example = "85") Integer severityScore,
        @Schema(description = "에러 타입", example = "NULL_POINTER") ErrorType errorType,
        @Schema(description = "스택 키", example = "GameController.process:42") String stackKey,
        @Schema(description = "발생 횟수", example = "42") Integer occurrenceCount,
        @Schema(description = "해결 노트", example = "null 체크 로직 추가") String resolutionNote,
        @Schema(description = "최초 발생 시각", example = "2024-03-11T10:00:00Z")
                OffsetDateTime firstOccurredAt,
        @Schema(description = "최근 발생 시각", example = "2024-03-11T15:30:00Z")
                OffsetDateTime lastOccurredAt,
        @Schema(description = "해결 시각", example = "2024-03-11T16:00:00Z") OffsetDateTime resolvedAt,
        @Schema(description = "생성 시각", example = "2024-03-11T10:00:00Z") OffsetDateTime createdAt,
        @Schema(description = "수정 시각", example = "2024-03-11T15:30:00Z") OffsetDateTime updatedAt,
        @Schema(description = "유사도 분석 결과 목록 (추천 이슈인 경우에만, 최대 4개)", nullable = true)
                List<SimilarityResult> similarityResults) {

    /**
     * Entity → DTO 변환 (담당자 정보 없음, 유사도 분석 없음)
     *
     * @param issue Issue 엔티티
     * @return IssueDetailResponse
     */
    public static IssueDetailResponse from(Issue issue) {
        return from(issue, null, null);
    }

    /**
     * Entity → DTO 변환 (유사도 분석 포함)
     *
     * @param issue Issue 엔티티
     * @param similarityResults 유사도 분석 결과 목록
     * @return IssueDetailResponse
     */
    public static IssueDetailResponse from(Issue issue, List<SimilarityResult> similarityResults) {
        return from(issue, null, similarityResults);
    }

    /**
     * Entity → DTO 변환 (담당자 정보 + 유사도 분석 포함)
     *
     * @param issue Issue 엔티티
     * @param assignee 담당자 정보
     * @param similarityResults 유사도 분석 결과 목록
     * @return IssueDetailResponse
     */
    public static IssueDetailResponse from(
            Issue issue, AssigneeInfo assignee, List<SimilarityResult> similarityResults) {
        return new IssueDetailResponse(
                issue.getId(),
                assignee,
                issue.getProjectId(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getFingerprint(),
                issue.getIssueType(),
                issue.getStatus(),
                issue.getPriority(),
                issue.getSeverity(),
                issue.getSeverityScore(),
                issue.getErrorType(),
                issue.getStackKey(),
                issue.getOccurrenceCount(),
                issue.getResolutionNote(),
                issue.getFirstOccurredAt(),
                issue.getLastOccurredAt(),
                issue.getResolvedAt(),
                issue.getCreatedAt(),
                issue.getUpdatedAt(),
                similarityResults);
    }
}

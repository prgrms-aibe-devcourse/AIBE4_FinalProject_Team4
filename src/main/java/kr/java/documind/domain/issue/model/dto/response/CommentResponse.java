package kr.java.documind.domain.issue.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import kr.java.documind.domain.issue.model.entity.Comment;

/**
 * 댓글 응답
 *
 * <p>GET /api/projects/{projectId}/issues/{issueId}/comments
 */
public record CommentResponse(
        @Schema(description = "댓글 ID", example = "1") Long id,
        @Schema(description = "이슈 ID", example = "101") Long issueId,
        @Schema(description = "작성자 정보") MemberSimpleInfo author,
        @Schema(description = "댓글 내용", example = "@김개발 이슈 확인 부탁드립니다.") String content,
        @Schema(description = "멘션된 사용자 목록") List<MemberSimpleInfo> mentionedMembers,
        @Schema(description = "생성 시각", example = "2024-03-11T10:30:00Z") OffsetDateTime createdAt,
        @Schema(description = "수정 시각", example = "2024-03-11T11:00:00Z") OffsetDateTime updatedAt) {

    /**
     * Entity → DTO 변환 (작성자 정보 포함)
     *
     * @param comment Comment 엔티티
     * @param author 작성자 정보
     * @param mentionedMembers 멘션된 사용자 목록
     * @return CommentResponse
     */
    public static CommentResponse from(
            Comment comment, MemberSimpleInfo author, List<MemberSimpleInfo> mentionedMembers) {
        return new CommentResponse(
                comment.getId(),
                comment.getIssueId(),
                author,
                comment.getContent(),
                mentionedMembers,
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}

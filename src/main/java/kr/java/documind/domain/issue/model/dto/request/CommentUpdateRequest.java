package kr.java.documind.domain.issue.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글 수정 요청
 *
 * <p>PUT /api/projects/{projectId}/issues/{issueId}/comments/{commentId}
 */
public record CommentUpdateRequest(
        @Schema(description = "수정할 댓글 내용 (@닉네임 형식으로 멘션 가능)", example = "@박테스터 수정된 내용입니다.")
                @NotBlank(message = "댓글 내용은 필수입니다")
                @Size(max = 2000, message = "댓글은 2000자를 초과할 수 없습니다")
                String content) {}

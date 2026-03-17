package kr.java.documind.domain.issue.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글 생성 요청
 *
 * <p>POST /api/projects/{projectId}/issues/{issueId}/comments
 */
public record CommentCreateRequest(
        @Schema(
                        description = "댓글 내용 (@닉네임 형식으로 멘션 가능)",
                        example = "@김개발 이슈 확인 부탁드립니다. 재현 방법은 다음과 같습니다...")
                @NotBlank(message = "댓글 내용은 필수입니다")
                @Size(max = 2000, message = "댓글은 2000자를 초과할 수 없습니다")
                String content) {}

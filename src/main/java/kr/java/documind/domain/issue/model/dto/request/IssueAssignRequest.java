package kr.java.documind.domain.issue.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 이슈 담당자 지정 요청
 *
 * <p>PUT /api/issues/{issueId}/assignee
 */
public record IssueAssignRequest(
        @Schema(description = "담당자 멤버 ID", example = "123e4567-e89b-12d3-a456-426614174000")
                @NotNull(message = "담당자 ID는 필수입니다")
                UUID assigneeId) {}

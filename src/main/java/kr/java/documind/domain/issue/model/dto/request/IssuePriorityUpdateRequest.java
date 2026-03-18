package kr.java.documind.domain.issue.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import kr.java.documind.domain.issue.model.enums.IssuePriority;

/**
 * 이슈 우선순위 변경 요청
 *
 * @param priority 변경할 우선순위 (P1/P2/P3/P4)
 */
@Schema(description = "이슈 우선순위 변경 요청")
public record IssuePriorityUpdateRequest(
        @Schema(description = "우선순위", example = "P2", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull(message = "우선순위는 필수입니다")
                IssuePriority priority) {}

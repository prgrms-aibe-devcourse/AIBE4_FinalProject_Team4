package kr.java.documind.domain.issue.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 추천 이슈 승인 요청
 *
 * @param assigneeId 담당자 ID (프로젝트 멤버)
 */
@Schema(description = "추천 이슈 승인 요청")
public record ApproveRecommendationRequest(
        @Schema(description = "담당자 ID (프로젝트 멤버)", example = "123e4567-e89b-12d3-a456-426614174001")
                UUID assigneeId) {}

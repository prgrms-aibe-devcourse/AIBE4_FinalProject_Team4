package kr.java.documind.domain.issue.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 추천 이슈 승인 요청
 *
 * @param assigneeId 담당자 ID (프로젝트 멤버)
 * @param title 이슈 제목 (수정 가능)
 * @param description 이슈 설명 (수정 가능)
 */
@Schema(description = "추천 이슈 승인 요청")
public record ApproveRecommendationRequest(
        @Schema(description = "담당자 ID (프로젝트 멤버)", example = "123e4567-e89b-12d3-a456-426614174001")
                UUID assigneeId,
        @Schema(description = "이슈 제목", example = "UserService에서 NullPointerException 발생")
                String title,
        @Schema(description = "이슈 설명", example = "사용자 프로필 조회 시 null 체크 없이 객체 접근하여 NPE 발생")
                String description) {}

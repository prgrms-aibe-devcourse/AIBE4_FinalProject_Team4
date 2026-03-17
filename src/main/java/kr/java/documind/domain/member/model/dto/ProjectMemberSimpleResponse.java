package kr.java.documind.domain.member.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 프로젝트 멤버 간단 정보 (드롭다운용) */
public record ProjectMemberSimpleResponse(
        @Schema(description = "멤버 ID", example = "123e4567-e89b-12d3-a456-426614174000")
                UUID memberId,
        @Schema(description = "닉네임", example = "홍길동") String nickname) {

    public static ProjectMemberSimpleResponse of(UUID memberId, String nickname) {
        return new ProjectMemberSimpleResponse(memberId, nickname);
    }
}

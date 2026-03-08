package kr.java.documind.domain.member.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로필 이미지 업로드 응답 DTO")
public record ProfileImageResponse(
        @Schema(
                        description = "S3 Pre-signed 접근 URL (15분 유효)",
                        example = "https://s3.../profile.png")
                String profileImageUrl) {}

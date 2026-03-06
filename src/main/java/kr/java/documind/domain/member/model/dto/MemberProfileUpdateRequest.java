package kr.java.documind.domain.member.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회원 프로필 수정 요청 DTO")
public record MemberProfileUpdateRequest(
        @Schema(description = "닉네임 (1~20자)", example = "도큐도큐")
                @NotBlank(message = "닉네임을 입력해주세요.")
                @Size(max = 20, message = "닉네임은 20자 이하로 입력해주세요.")
                String nickname) {}

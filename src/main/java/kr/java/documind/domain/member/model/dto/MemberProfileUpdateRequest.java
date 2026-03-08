package kr.java.documind.domain.member.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "회원 프로필 수정 요청 DTO")
public record MemberProfileUpdateRequest(
        @Schema(description = "닉네임 (1~20자, 한글/영문/숫자/공백 허용 / null이면 변경하지 않음)", example = "도큐도큐")
                @Size(max = 20, message = "닉네임은 20자 이하로 입력해주세요.")
                @Pattern(
                        regexp = "^[가-힣a-zA-Z0-9 ]*$",
                        message = "닉네임은 한글, 영문, 숫자, 공백만 사용할 수 있습니다.")
                String nickname,
        @Schema(description = "직급 (최대 20자, 한글/영문/숫자/공백 허용 / 선택)", example = "시니어 개발자")
                @Size(max = 20, message = "직급은 20자 이하로 입력해주세요.")
                @Pattern(regexp = "^[가-힣a-zA-Z0-9 ]*$", message = "직급은 한글, 영문, 숫자, 공백만 사용할 수 있습니다.")
                String position) {}

package kr.java.documind.domain.member.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.java.documind.domain.auth.model.enums.ProjectRole;

@Schema(description = "멤버 초대 요청 DTO")
public record InviteSendRequest(
        @Schema(description = "초대받을 사용자 이메일", example = "member@example.com")
                @NotBlank(message = "이메일을 입력해주세요.")
                @Email(message = "올바른 이메일 형식이 아닙니다.")
                String targetEmail,
        @Schema(description = "부여할 프로젝트 역할 (MANAGER | MEMBER)", example = "MEMBER")
                @NotNull(message = "역할을 선택해주세요.")
                ProjectRole targetRole) {}

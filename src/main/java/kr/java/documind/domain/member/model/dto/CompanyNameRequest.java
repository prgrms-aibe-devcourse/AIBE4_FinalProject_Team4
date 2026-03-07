package kr.java.documind.domain.member.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회사 이름 요청 DTO (등록·수정 공용)")
public record CompanyNameRequest(
        @Schema(description = "회사/조직명 (1~100자)", example = "Documind Corp.")
                @NotBlank(message = "회사명을 입력해주세요.")
                @Size(max = 100, message = "회사명은 100자 이하로 입력해주세요.")
                String name) {}

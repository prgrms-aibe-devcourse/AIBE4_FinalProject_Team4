package kr.java.documind.domain.member.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.java.documind.global.annotation.ValidName;

@Schema(description = "회사 이름 요청 DTO (등록·수정 공용)")
public record CompanyNameRequest(
        @Schema(description = "회사/조직명 (1~100자, 한글/영문/숫자/공백 허용)", example = "Documind Corp")
                @ValidName(
                        max = 100,
                        notBlankMessage = "회사명을 입력해주세요.",
                        maxMessage = "회사명은 100자 이하로 입력해주세요.")
                String name) {}

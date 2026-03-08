package kr.java.documind.domain.member.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "프로젝트 정보 수정 요청 DTO")
public record ProjectUpdateRequest(
        @Schema(description = "변경할 프로젝트 이름", example = "Project RPG v2")
                @NotBlank(message = "프로젝트 이름을 입력해주세요.")
                @Size(max = 100, message = "프로젝트 이름은 100자 이하로 입력해주세요.")
                String name) {}

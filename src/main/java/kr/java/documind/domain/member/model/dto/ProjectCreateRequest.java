package kr.java.documind.domain.member.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectCreateRequest(
        @NotBlank(message = "프로젝트 이름을 입력해주세요.")
                @Size(max = 100, message = "프로젝트 이름은 100자 이하로 입력해주세요.")
                String name) {}

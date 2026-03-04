package kr.java.documind.domain.archive.document.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank(message = "카테고리를 선택해주세요.") @Size(max = 10, message = "카테고리는 10자 이내로 입력해주세요.")
                String category) {}

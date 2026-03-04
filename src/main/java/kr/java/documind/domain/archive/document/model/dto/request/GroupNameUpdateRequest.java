package kr.java.documind.domain.archive.document.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupNameUpdateRequest(
        @NotBlank(message = "그룹명을 입력해주세요.") @Size(max = 30, message = "그룹명은 30자 이내로 입력해주세요.")
                String groupName) {}

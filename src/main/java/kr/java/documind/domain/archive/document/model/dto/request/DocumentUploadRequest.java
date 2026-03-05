package kr.java.documind.domain.archive.document.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentUploadRequest(
        @NotBlank(message = "그룹명을 입력해주세요.") @Size(max = 30, message = "그룹명은 30자 이내로 입력해주세요.")
                String groupName,
        @NotBlank(message = "카테고리를 선택해주세요.") @Size(max = 10, message = "카테고리는 10자 이내로 입력해주세요.")
                String category,
        @NotNull(message = "메이저 버전을 입력해주세요.")
                @Min(value = 0, message = "버전은 0 이상이어야 합니다.")
                @Max(value = 999, message = "버전은 999 이하여야 합니다.")
                Integer majorVersion,
        @NotNull(message = "마이너 버전을 입력해주세요.")
                @Min(value = 0, message = "버전은 0 이상이어야 합니다.")
                @Max(value = 999, message = "버전은 999 이하여야 합니다.")
                Integer minorVersion,
        @NotNull(message = "패치 버전을 입력해주세요.")
                @Min(value = 0, message = "버전은 0 이상이어야 합니다.")
                @Max(value = 999, message = "버전은 999 이하여야 합니다.")
                Integer patchVersion,
        Boolean isProcessed)
        implements VersionFields {}

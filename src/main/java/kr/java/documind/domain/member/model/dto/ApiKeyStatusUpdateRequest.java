package kr.java.documind.domain.member.model.dto;

import jakarta.validation.constraints.NotNull;
import kr.java.documind.domain.member.model.enums.ApiKeyStatus;
import kr.java.documind.domain.auth.model.enums.ApiKeyType;

public record ApiKeyStatusUpdateRequest(
    @NotNull(message = "변경할 API 키의 타입을 지정해주세요.")
    ApiKeyType keyType,

    @NotNull(message = "변경할 상태를 지정해주세요. (ACTIVE 또는 SUSPENDED)")
    ApiKeyStatus status
) {}

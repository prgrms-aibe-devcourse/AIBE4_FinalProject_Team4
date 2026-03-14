package kr.java.documind.domain.member.model.dto;

import jakarta.validation.constraints.NotNull;
import kr.java.documind.domain.auth.model.enums.ApiKeyType;

public record ApiKeyReissueRequest(
        @NotNull(message = "재발급할 API 키의 타입을 지정해주세요.") ApiKeyType keyType) {}

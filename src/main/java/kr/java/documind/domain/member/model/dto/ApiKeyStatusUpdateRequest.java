package kr.java.documind.domain.member.model.dto;

import jakarta.validation.constraints.NotNull;
import kr.java.documind.domain.member.model.enums.ApiKeyStatus;

public record ApiKeyStatusUpdateRequest(@NotNull ApiKeyStatus status) {}

package kr.java.documind.domain.member.model.dto;

import jakarta.validation.constraints.NotNull;
import kr.java.documind.domain.auth.model.enums.ProjectRole;

public record ProjectRoleUpdateRequest(@NotNull ProjectRole role) {}

package kr.java.documind.domain.auth.model.dto;

import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.auth.model.enums.OAuthProvider;

public record ConflictingMemberInfo(
        OAuthProvider provider,
        String nickname,
        String email,
        String profileImageUrl,
        GlobalRole globalRole) {}

package kr.java.documind.domain.member.model.dto;

import kr.java.documind.domain.member.model.enums.GlobalRole;
import kr.java.documind.domain.member.model.enums.OAuthProvider;

public record ConflictingMemberInfo(
        OAuthProvider provider,
        String nickname,
        String email,
        String profileImageUrl,
        GlobalRole globalRole) {}

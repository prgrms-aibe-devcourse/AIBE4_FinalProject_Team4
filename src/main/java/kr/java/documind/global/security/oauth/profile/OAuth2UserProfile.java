package kr.java.documind.global.security.oauth.profile;

import kr.java.documind.domain.member.model.enums.OAuthProvider;

public interface OAuth2UserProfile {

    String getId();

    String getName();

    String getNickname();

    String getEmail();

    String getImageUrl();

    OAuthProvider getProvider();
}

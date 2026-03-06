package kr.java.documind.global.security.oauth.profile;

import java.util.Map;
import kr.java.documind.domain.member.model.enums.OAuthProvider;

public class GoogleUserProfile implements OAuth2UserProfile {

    private final Map<String, Object> attributes;

    public GoogleUserProfile(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getId() {
        return (String) attributes.get("sub");
    }

    @Override
    public String getName() {
        return (String) attributes.get("name");
    }

    @Override
    public String getNickname() {
        return (String) attributes.get("name");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getImageUrl() {
        return (String) attributes.get("picture");
    }

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.GOOGLE;
    }
}

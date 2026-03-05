package kr.java.documind.global.security.oauth.profile;

import java.util.Map;
import kr.java.documind.domain.member.model.enums.OAuthProvider;

public class GithubUserProfile implements OAuth2UserProfile {

    private final Map<String, Object> attributes;

    public GithubUserProfile(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getId() {
        Object id = attributes.get("id");
        return id != null ? id.toString() : null;
    }

    @Override
    public String getName() {
        String name = (String) attributes.get("name");
        return (name != null && !name.isBlank()) ? name : (String) attributes.get("login");
    }

    @Override
    public String getNickname() {
        return (String) attributes.get("login");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getImageUrl() {
        return (String) attributes.get("avatar_url");
    }

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.GITHUB;
    }
}

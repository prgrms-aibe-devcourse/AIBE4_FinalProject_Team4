package kr.java.documind.global.security.oauth.profile;

import java.util.Map;

public class OAuthUserProfileFactory {

    private OAuthUserProfileFactory() {}

    public static OAuth2UserProfile createOAuth2UserProfile(
            String registrationId, Map<String, Object> attributes) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> new GoogleUserProfile(attributes);
            case "github" -> new GithubUserProfile(attributes);
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 OAuth2 Provider: " + registrationId);
        };
    }
}

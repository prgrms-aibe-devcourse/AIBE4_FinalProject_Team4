package kr.java.documind.global.security.oauth;

import java.util.Map;
import java.util.Set;

public record OAuth2AuthorizationState(
        String clientId,
        String authorizationUri,
        String redirectUri,
        Set<String> scopes,
        String state,
        Map<String, Object> additionalParameters,
        String authorizationRequestUri,
        Map<String, Object> attributes) {}

package kr.java.documind.global.security.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.global.config.JwtProperties;
import kr.java.documind.global.security.RedisTokenService;
import kr.java.documind.global.util.CookieUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    public static final String REQUEST_ID_COOKIE = "oauth2_request_id";
    public static final String REDIRECT_AFTER_LOGIN_COOKIE = "redirect_after_login";

    private static final long COOKIE_TTL_SECONDS = 300L; // 5분

    private final CookieUtil cookieUtil;
    private final RedisTokenService redisTokenService;
    private final ObjectMapper objectMapper;
    private final JwtProperties jwtProperties;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return cookieUtil
                .getCookieValue(request, REQUEST_ID_COOKIE)
                .flatMap(
                        requestId -> {
                            String stateJson = redisTokenService.getOAuth2State(requestId);
                            if (stateJson == null) {
                                log.debug(
                                        "[OAuth2RequestRepository] OAuth2 상태 만료 또는 미존재: requestId={}",
                                        requestId);
                                return Optional.empty();
                            }
                            return Optional.ofNullable(toAuthorizationRequest(stateJson));
                        })
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (authorizationRequest == null) {
            removeAuthorizationRequestCookies(request, response);
            return;
        }

        String requestId = UUID.randomUUID().toString();

        String stateJson = toJson(authorizationRequest);
        if (stateJson == null) {
            log.warn("[OAuth2RequestRepository] OAuth2AuthorizationRequest 직렬화 실패 — OAuth2 흐름 중단");
            return;
        }
        redisTokenService.saveOAuth2State(requestId, stateJson, COOKIE_TTL_SECONDS);

        boolean secure = jwtProperties.isCookieSecure();
        cookieUtil.addCookie(response, REQUEST_ID_COOKIE, requestId, COOKIE_TTL_SECONDS, secure);

        String redirectAfterLogin = request.getParameter(REDIRECT_AFTER_LOGIN_COOKIE);
        if (redirectAfterLogin != null && !redirectAfterLogin.isBlank()) {
            cookieUtil.addCookie(
                    response,
                    REDIRECT_AFTER_LOGIN_COOKIE,
                    redirectAfterLogin,
                    COOKIE_TTL_SECONDS,
                    secure);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {

        Optional<String> requestId = cookieUtil.getCookieValue(request, REQUEST_ID_COOKIE);

        OAuth2AuthorizationRequest stored =
                requestId
                        .map(
                                id -> {
                                    String json = redisTokenService.getOAuth2State(id);
                                    redisTokenService.deleteOAuth2State(id);
                                    return json != null ? toAuthorizationRequest(json) : null;
                                })
                        .orElse(null);

        boolean secure = jwtProperties.isCookieSecure();
        cookieUtil.deleteCookie(response, REQUEST_ID_COOKIE, secure);
        cookieUtil.deleteCookie(response, REDIRECT_AFTER_LOGIN_COOKIE, secure);

        return stored;
    }

    public void removeAuthorizationRequestCookies(
            HttpServletRequest request, HttpServletResponse response) {

        cookieUtil
                .getCookieValue(request, REQUEST_ID_COOKIE)
                .ifPresent(redisTokenService::deleteOAuth2State);

        boolean secure = jwtProperties.isCookieSecure();
        cookieUtil.deleteCookie(response, REQUEST_ID_COOKIE, secure);
        cookieUtil.deleteCookie(response, REDIRECT_AFTER_LOGIN_COOKIE, secure);
    }

    private String toJson(OAuth2AuthorizationRequest req) {
        try {
            OAuth2AuthorizationState state =
                    new OAuth2AuthorizationState(
                            req.getClientId(),
                            req.getAuthorizationUri(),
                            req.getRedirectUri(),
                            req.getScopes(),
                            req.getState(),
                            req.getAdditionalParameters(),
                            req.getAuthorizationRequestUri(),
                            req.getAttributes());
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            log.warn(
                    "[OAuth2RequestRepository] OAuth2AuthorizationRequest 직렬화 실패: {}",
                    e.getMessage());
            return null;
        }
    }

    private OAuth2AuthorizationRequest toAuthorizationRequest(String stateJson) {
        try {
            OAuth2AuthorizationState s =
                    objectMapper.readValue(stateJson, OAuth2AuthorizationState.class);
            return OAuth2AuthorizationRequest.authorizationCode()
                    .clientId(s.clientId())
                    .authorizationUri(s.authorizationUri())
                    .redirectUri(s.redirectUri())
                    .scopes(s.scopes())
                    .state(s.state())
                    .additionalParameters(s.additionalParameters())
                    .authorizationRequestUri(s.authorizationRequestUri())
                    .attributes(s.attributes())
                    .build();
        } catch (Exception e) {
            log.warn(
                    "[OAuth2RequestRepository] OAuth2AuthorizationRequest 역직렬화 실패: {}",
                    e.getMessage());
            return null;
        }
    }
}

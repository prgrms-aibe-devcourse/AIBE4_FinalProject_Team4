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
            // 입력값 검증: /로 시작하고 //로 시작하지 않아야 함 (Open Redirect 방지)
            if (redirectAfterLogin.startsWith("/") && !redirectAfterLogin.startsWith("//")) {
                cookieUtil.addCookie(
                        response,
                        REDIRECT_AFTER_LOGIN_COOKIE,
                        redirectAfterLogin,
                        COOKIE_TTL_SECONDS,
                        secure);
            } else {
                log.warn("[OAuth2RequestRepository] 유효하지 않은 리다이렉트 URL 무시: {}", redirectAfterLogin);
            }
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
        // redirect_after_login은 onAuthenticationSuccess()에서 읽은 뒤 삭제하므로 여기서 제거하지 않음

        return stored;
    }

    /** 성공 핸들러 전용: OAuth2 상태 쿠키와 리다이렉트 쿠키를 모두 삭제한다. 로그인이 완료된 후에만 호출해야 한다. */
    public void removeAuthorizationRequestCookies(
            HttpServletRequest request, HttpServletResponse response) {

        cookieUtil
                .getCookieValue(request, REQUEST_ID_COOKIE)
                .ifPresent(redisTokenService::deleteOAuth2State);

        boolean secure = jwtProperties.isCookieSecure();
        cookieUtil.deleteCookie(response, REQUEST_ID_COOKIE, secure);
        cookieUtil.deleteCookie(response, REDIRECT_AFTER_LOGIN_COOKIE, secure);
    }

    /**
     * 실패 핸들러 전용: OAuth2 상태 쿠키만 삭제하고 redirect_after_login은 보존한다. 초대 플로우에서 재시도 시 리다이렉트 목적지를 유지하기 위해
     * 사용한다.
     */
    public void removeOAuth2StateOnly(HttpServletRequest request, HttpServletResponse response) {

        cookieUtil
                .getCookieValue(request, REQUEST_ID_COOKIE)
                .ifPresent(redisTokenService::deleteOAuth2State);

        cookieUtil.deleteCookie(response, REQUEST_ID_COOKIE, jwtProperties.isCookieSecure());
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

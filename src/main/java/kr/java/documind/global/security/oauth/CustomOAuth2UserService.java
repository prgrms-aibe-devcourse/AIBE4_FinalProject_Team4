package kr.java.documind.global.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.GlobalRole;
import kr.java.documind.domain.member.model.enums.OAuthProvider;
import kr.java.documind.domain.member.service.MemberService;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import kr.java.documind.global.security.oauth.profile.OAuth2UserProfile;
import kr.java.documind.global.security.oauth.profile.OAuthUserProfileFactory;
import kr.java.documind.global.util.CookieUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    static final String PENDING_ROLE_COOKIE = "oauth_pending_role";

    private final MemberService memberService;
    private final CookieUtil cookieUtil;

    private final OidcUserService oidcUserService = new OidcUserService();

    private final RestClient githubRestClient =
            RestClient.builder().baseUrl("https://api.github.com").build();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        return processOAuthLogin(
                userRequest.getClientRegistration().getRegistrationId(),
                oAuth2User.getAttributes(),
                userRequest);
    }

    public OidcUser loadOidcUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = oidcUserService.loadUser(userRequest);
        CustomUserDetails processed =
                processOAuthLogin(
                        userRequest.getClientRegistration().getRegistrationId(),
                        oidcUser.getAttributes(),
                        userRequest);
        return new CustomUserDetails(
                processed.getMemberId(),
                processed.getGlobalRole(),
                oidcUser.getAttributes(),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo());
    }

    private CustomUserDetails processOAuthLogin(
            String registrationId, Map<String, Object> attributes, OAuth2UserRequest userRequest) {
        OAuth2UserProfile userProfile =
                OAuthUserProfileFactory.createOAuth2UserProfile(registrationId, attributes);

        String resolvedEmail = resolveEmail(userProfile, userRequest);
        GlobalRole role = resolveRoleFromCookie();

        String name =
                Optional.ofNullable(userProfile.getName()).filter(n -> !n.isBlank()).orElse("사용자");
        String nickname =
                Optional.ofNullable(userProfile.getNickname())
                        .filter(n -> !n.isBlank())
                        .orElse(name);

        Member member =
                memberService.findOrCreateOAuthMember(
                        userProfile.getProvider(),
                        userProfile.getId(),
                        resolvedEmail,
                        name,
                        nickname,
                        role);

        return new CustomUserDetails(member.getId(), member.getGlobalRole(), attributes);
    }

    private String resolveEmail(OAuth2UserProfile userProfile, OAuth2UserRequest userRequest) {
        String email = userProfile.getEmail();
        if (email != null && !email.isBlank()) {
            return email;
        }

        // GitHub 비공개 이메일: /user/emails API 추가 호출
        if (userProfile.getProvider() == OAuthProvider.GITHUB) {
            String accessToken = userRequest.getAccessToken().getTokenValue();
            String githubEmail = fetchGithubVerifiedEmail(accessToken);
            if (githubEmail != null) {
                log.debug("GitHub /user/emails API로 이메일 조회 성공");
                return githubEmail;
            }
        }

        log.warn(
                "이메일 조회 실패 — placeholder 저장: provider={} id={}",
                userProfile.getProvider(),
                userProfile.getId());
        return buildEmailPlaceholder(userProfile);
    }

    private String fetchGithubVerifiedEmail(String accessToken) {
        try {
            List<Map<String, Object>> emails =
                    githubRestClient
                            .get()
                            .uri("/user/emails")
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Accept", "application/vnd.github+json")
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});

            if (emails == null || emails.isEmpty()) {
                return null;
            }

            return emails.stream()
                    .filter(
                            e ->
                                    Boolean.TRUE.equals(e.get("primary"))
                                            && Boolean.TRUE.equals(e.get("verified")))
                    .map(e -> (String) e.get("email"))
                    .findFirst()
                    .or(
                            () ->
                                    emails.stream()
                                            .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                                            .map(e -> (String) e.get("email"))
                                            .findFirst())
                    .orElse(null);

        } catch (Exception e) {
            log.warn("GitHub /user/emails API 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    private GlobalRole resolveRoleFromCookie() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();

            return cookieUtil
                    .getCookieValue(request, PENDING_ROLE_COOKIE)
                    .map(
                            value -> {
                                try {
                                    GlobalRole role = GlobalRole.valueOf(value.toUpperCase());
                                    return (role == GlobalRole.CEO || role == GlobalRole.EMPLOYEE)
                                            ? role
                                            : GlobalRole.EMPLOYEE;
                                } catch (IllegalArgumentException e) {
                                    return GlobalRole.EMPLOYEE;
                                }
                            })
                    .orElse(GlobalRole.EMPLOYEE);
        } catch (Exception e) {
            log.debug("oauth_pending_role 쿠키 읽기 실패: {}", e.getMessage());
            return GlobalRole.EMPLOYEE;
        }
    }

    private String buildEmailPlaceholder(OAuth2UserProfile userProfile) {
        return userProfile.getProvider().name().toLowerCase()
                + "_"
                + userProfile.getId()
                + "@oauth.placeholder";
    }
}

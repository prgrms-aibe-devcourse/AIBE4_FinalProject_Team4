package kr.java.documind.global.config;

import kr.java.documind.global.security.RedisTokenService;
import kr.java.documind.global.security.filter.CsrfCookieFilter;
import kr.java.documind.global.security.filter.JwtAuthenticationFilter;
import kr.java.documind.global.security.jwt.CustomAccessDeniedHandler;
import kr.java.documind.global.security.jwt.CustomAuthenticationEntryPoint;
import kr.java.documind.global.security.jwt.TokenProvider;
import kr.java.documind.global.security.oauth.CustomOAuth2UserService;
import kr.java.documind.global.security.oauth.HttpCookieOAuth2AuthorizationRequestRepository;
import kr.java.documind.global.security.oauth.OAuth2FailureHandler;
import kr.java.documind.global.security.oauth.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final CustomAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler jwtAccessDeniedHandler;
    private final RedisTokenService redisTokenService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository authRequestRepository;

    /** 인증 없이 접근 가능한 GET 경로 (정적 리소스 · 공개 페이지) */
    private static final String[] PUBLIC_GET_PATHS = {
        "/",
        "/auth/login",
        "/preview/**",
        "/invite/**", // /invite/{token} 페이지 접근 허용
        "/error",
        "/css/**",
        "/js/**",
        "/images/**",
        "/fonts/**",
        "/favicon.ico",
        "/swagger-ui/**",
        "/v3/api-docs/**",
    };

    /** 인증 없이 접근 가능한 POST 경로 */
    private static final String[] PUBLIC_POST_PATHS = {
        "/api/auth/refresh", "/api/auth/logout", "/api/logs"
    };

    /** 인증 없이 접근 가능한 Actuator 엔드포인트 */
    private static final String[] PUBLIC_ACTUATOR_PATHS = {
        "/actuator/health",
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(csrfTokenRepository())
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        .ignoringRequestMatchers(
                                                "/oauth2/**",
                                                "/login/oauth2/**",
                                                "/api/**",
                                                "/invite/accept"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(
                        auth ->
                                auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                                        .requestMatchers(HttpMethod.POST, "/invite/accept")
                                        .authenticated()
                                        // ── 공개 페이지 / 정적 리소스 ──────────────────────
                                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, PUBLIC_ACTUATOR_PATHS)
                                        .permitAll()
                                        .requestMatchers("/actuator/**")
                                        .hasRole("ADMIN")
                                        // ── OAuth2 관련 경로 ──────────────────────────────
                                        .requestMatchers("/oauth2/**", "/login/oauth2/**")
                                        .permitAll()
                                        // ── 어드민 전용 페이지 및 API ───────────────────────
                                        .requestMatchers("/admin/**", "/api/admin/**")
                                        .hasRole("ADMIN")
                                        // ── 그 외 모든 요청: 인증 필요 ───────────────────
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(jwtAuthenticationEntryPoint) // 401
                                        .accessDeniedHandler(jwtAccessDeniedHandler) // 403
                        )
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(
                        jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(
                        oauth2 ->
                                oauth2.authorizationEndpoint(
                                                endpoint ->
                                                        endpoint.authorizationRequestRepository(
                                                                authRequestRepository))
                                        .redirectionEndpoint(
                                                endpoint ->
                                                        endpoint.baseUri("/login/oauth2/code/*"))
                                        .userInfoEndpoint(
                                                userInfo ->
                                                        userInfo.userService(
                                                                        customOAuth2UserService)
                                                                .oidcUserService(
                                                                        customOAuth2UserService
                                                                                ::loadOidcUser))
                                        .successHandler(oAuth2SuccessHandler)
                                        .failureHandler(oAuth2FailureHandler))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtProvider, jwtProperties, redisTokenService);
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(builder -> builder.sameSite("Lax"));
        return repository;
    }
}

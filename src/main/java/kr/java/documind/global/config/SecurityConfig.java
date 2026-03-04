package kr.java.documind.global.config;

import kr.java.documind.global.security.RedisTokenService;
import kr.java.documind.global.security.jwt.CustomAccessDeniedHandler;
import kr.java.documind.global.security.jwt.CustomAuthenticationEntryPoint;
import kr.java.documind.global.security.jwt.JwtAuthenticationFilter;
import kr.java.documind.global.security.jwt.TokenProvider;
import kr.java.documind.global.security.oauth.CustomOAuth2UserService;
import kr.java.documind.global.security.oauth.HttpCookieOAuth2AuthorizationRequestRepository;
import kr.java.documind.global.security.oauth.OAuth2FailureHandler;
import kr.java.documind.global.security.oauth.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

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

    private static final String[] PUBLIC_GET_PATHS = {
        "/",
        "/auth/login",
        "/invite/**",
        "/error",
        "/css/**",
        "/js/**",
        "/images/**",
        "/favicon.ico"
    };

    private static final String[] PUBLIC_POST_PATHS = {
        "/api/auth/refresh",
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
                                                new AntPathRequestMatcher("/oauth2/**"),
                                                new AntPathRequestMatcher("/login/oauth2/**")))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(PUBLIC_GET_PATHS)
                                        .permitAll()
                                        .requestMatchers(PUBLIC_POST_PATHS)
                                        .permitAll()
                                        .requestMatchers("/actuator/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(jwtAuthenticationEntryPoint) // 401
                                        .accessDeniedHandler(jwtAccessDeniedHandler) // 403
                        )
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
                                                                customOAuth2UserService))
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
        repository.setCookieCustomizer(builder -> builder.sameSite("Strict"));
        return repository;
    }
}

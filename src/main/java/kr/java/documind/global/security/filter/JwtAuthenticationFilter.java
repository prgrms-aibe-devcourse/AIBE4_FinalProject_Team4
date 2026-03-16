package kr.java.documind.global.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.global.config.JwtProperties;
import kr.java.documind.global.exception.UnauthorizedException;
import kr.java.documind.global.security.RedisTokenService;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import kr.java.documind.global.security.jwt.TokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final RedisTokenService redisTokenService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String accessToken = extractToken(request);

        if (!StringUtils.hasText(accessToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticateIfValidAccessToken(request, accessToken);
        } catch (UnauthorizedException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            log.warn(
                    "[JWT] 유효하지 않은 JWT: SecurityContext 초기화 - {} ({})",
                    request.getRequestURI(),
                    e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateIfValidAccessToken(HttpServletRequest request, String accessToken) {
        tokenProvider.validateAccessToken(accessToken);

        if (redisTokenService.isBlacklisted(accessToken)) {
            SecurityContextHolder.clearContext();
            log.debug(
                    "[JWT] Blacklisted JWT — cleared SecurityContext for {}",
                    request.getRequestURI());
            return;
        }

        UUID memberId = tokenProvider.getMemberId(accessToken);
        if (redisTokenService.isMemberSuspended(memberId)) {
            SecurityContextHolder.clearContext();
            log.warn("[JWT] 정지된 계정 요청 차단: memberId={} uri={}", memberId, request.getRequestURI());
            return;
        }

        setAuthentication(request, accessToken, memberId);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return extractTokenFromCookie(request);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> jwtProperties.getAccessCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private void setAuthentication(HttpServletRequest request, String accessToken, UUID memberId) {

        GlobalRole globalRole = tokenProvider.getGlobalRole(accessToken);

        CustomUserDetails authMember = new CustomUserDetails(memberId, globalRole);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        authMember, null, authMember.getAuthorities());

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

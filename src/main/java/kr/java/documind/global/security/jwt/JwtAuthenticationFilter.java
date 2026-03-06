package kr.java.documind.global.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import kr.java.documind.domain.member.model.enums.GlobalRole;
import kr.java.documind.global.config.JwtProperties;
import kr.java.documind.global.security.RedisTokenService;
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
        String token = extractTokenFromCookie(request);

        if (StringUtils.hasText(token)
                && tokenProvider.validateToken(token)
                && tokenProvider.isAccessToken(token)) {
            if (redisTokenService.isBlacklisted(token)) {
                SecurityContextHolder.clearContext();
                log.debug(
                        "Blacklisted JWT — cleared SecurityContext for {}",
                        request.getRequestURI());
            } else {
                setAuthentication(request, token);
            }
        } else if (StringUtils.hasText(token)) {
            SecurityContextHolder.clearContext();
            log.debug("[JWT] 만료되거나 유효하지 않은 JWT: SecurityContext 초기화 - {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
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

    private void setAuthentication(HttpServletRequest request, String token) {
        try {
            UUID memberId = tokenProvider.getMemberId(token);
            GlobalRole globalRole = tokenProvider.getGlobalRole(token);

            CustomUserDetails authMember = new CustomUserDetails(memberId, globalRole);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            authMember, null, authMember.getAuthorities());

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.warn("[Jwt] JWT 클레임에서 CustomUserDetail 생성 실패: {} ", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }
}

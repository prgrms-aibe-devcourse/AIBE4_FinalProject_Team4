package kr.java.documind.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import kr.java.documind.domain.member.model.enums.GlobalRole;
import kr.java.documind.global.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String CLAIM_TOKEN_TYPE = "type";

    private final JwtProperties jwtProperties;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID memberId, GlobalRole globalRole) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessExpirationSeconds() * 1000L);

        return Jwts.builder()
                .subject(memberId.toString())
                .claim(CLAIM_ROLE, globalRole.name())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(UUID memberId, GlobalRole globalRole) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshExpirationSeconds() * 1000L);

        return Jwts.builder()
                .subject(memberId.toString())
                .claim(CLAIM_ROLE, globalRole.name())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public UUID getMemberId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public GlobalRole getGlobalRole(String token) {
        String role = parseClaims(token).get(CLAIM_ROLE, String.class);
        return GlobalRole.valueOf(role);
    }

    public Date getExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    public long getRemainingMillis(String token) {
        return getExpiration(token).getTime() - System.currentTimeMillis();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("[JWT] 토큰 만료: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[JWT] 토큰 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    public UUID getMemberIdFromExpiredToken(String token) {
        try {
            return UUID.fromString(parseClaims(token).getSubject());
        } catch (ExpiredJwtException e) {
            return UUID.fromString(e.getClaims().getSubject());
        }
    }

    public boolean isAccessToken(String token) {
        try {
            String type = parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class);
            return TOKEN_TYPE_ACCESS.equals(type);
        } catch (JwtException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }
}

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
import kr.java.documind.domain.auth.exception.InvalidTokenException;
import kr.java.documind.domain.auth.exception.TokenExpiredException;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.global.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "type";

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID memberId, GlobalRole globalRole) {
        return generateToken(
                memberId,
                globalRole,
                TOKEN_TYPE_ACCESS,
                jwtProperties.getAccessExpirationSeconds());
    }

    public String generateRefreshToken(UUID memberId, GlobalRole globalRole) {
        return generateToken(
                memberId,
                globalRole,
                TOKEN_TYPE_REFRESH,
                jwtProperties.getRefreshExpirationSeconds());
    }

    public void validateRefreshToken(String token) {
        Claims claims = parseValidClaims(token);

        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new InvalidTokenException();
        }
    }

    public void validateAccessToken(String token) {
        Claims claims = parseValidClaims(token);

        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
            throw new InvalidTokenException();
        }
    }

    public UUID getMemberId(String token) {
        try {
            return UUID.fromString(parseClaims(token).getSubject());
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }

    public UUID getMemberIdAllowExpired(String token) {
        try {
            return UUID.fromString(parseClaims(token).getSubject());
        } catch (ExpiredJwtException e) {
            return UUID.fromString(e.getClaims().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }

    public GlobalRole getGlobalRole(String token) {
        try {
            String role = parseClaims(token).get(CLAIM_ROLE, String.class);
            return GlobalRole.valueOf(role);
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }

    public String getTokenId(String token) {
        try {
            return parseClaims(token).getId();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getId();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }

    public long getRemainingMillis(String token) {
        try {
            Date expiration = parseClaims(token).getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getExpiration().getTime() - System.currentTimeMillis();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[JWT] 토큰 만료 시간 추출 실패: {}", e.getMessage());
            return 0L;
        }
    }

    public boolean isAccessToken(String token) {
        return hasTokenType(token, TOKEN_TYPE_ACCESS);
    }

    public boolean isRefreshToken(String token) {
        return hasTokenType(token, TOKEN_TYPE_REFRESH);
    }

    private Claims parseValidClaims(String token) {
        try {
            return parseClaims(token);
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }

    private boolean hasTokenType(String token, String expectedType) {
        try {
            String actualType = parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class);
            return expectedType.equals(actualType);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private String generateToken(
            UUID memberId, GlobalRole globalRole, String tokenType, long expirationSeconds) {

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000L);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(memberId.toString())
                .claim(CLAIM_ROLE, globalRole.name())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }
}

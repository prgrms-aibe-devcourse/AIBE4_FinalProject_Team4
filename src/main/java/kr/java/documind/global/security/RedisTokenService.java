package kr.java.documind.global.security;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import kr.java.documind.global.security.jwt.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private static final String REFRESH_PREFIX = "refresh:";
    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String OAUTH2_STATE_PREFIX = "oauth2_state:";
    private static final String SUSPENDED_PREFIX = "suspended:";

    private final StringRedisTemplate redisTemplate;
    private final TokenProvider tokenProvider;
    private final RedisScript<Long> rotateRefreshTokenScript;

    @Value("${app.jwt.redis-prefix:jwt:}")
    private String jwtPrefix;

    public void saveRefreshToken(UUID memberId, String refreshToken, long ttlSeconds) {
        redisTemplate
                .opsForValue()
                .set(refreshKey(memberId), refreshToken, ttlSeconds, TimeUnit.SECONDS);
    }

    public String getRefreshToken(UUID memberId) {
        return redisTemplate.opsForValue().get(refreshKey(memberId));
    }

    public boolean rotateRefreshToken(
            UUID memberId,
            String expectedOldRefreshToken,
            String newRefreshToken,
            long ttlSeconds) {

        Long result =
                redisTemplate.execute(
                        rotateRefreshTokenScript,
                        Collections.singletonList(refreshKey(memberId)),
                        expectedOldRefreshToken,
                        newRefreshToken,
                        String.valueOf(ttlSeconds));

        return Long.valueOf(1L).equals(result);
    }

    public void deleteRefreshToken(UUID memberId) {
        redisTemplate.delete(refreshKey(memberId));
    }

    public void addToBlacklist(String accessToken, long ttlMillis) {
        if (ttlMillis <= 0) {
            return;
        }

        long ttlSeconds = Math.max(1L, (ttlMillis + 999L) / 1000L);
        redisTemplate
                .opsForValue()
                .set(blacklistKey(accessToken), "1", ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey(accessToken)));
    }

    public void revokeAllTokensByMember(UUID memberId, long accessTokenTtlSeconds) {
        deleteRefreshToken(memberId);
        redisTemplate
                .opsForValue()
                .set(suspendedKey(memberId), "1", accessTokenTtlSeconds, TimeUnit.SECONDS);
    }

    public boolean isMemberSuspended(UUID memberId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(suspendedKey(memberId)));
    }

    public void clearSuspension(UUID memberId) {
        redisTemplate.delete(suspendedKey(memberId));
    }

    public void saveOAuth2State(String requestId, String stateJson, long ttlSeconds) {
        redisTemplate
                .opsForValue()
                .set(oauth2StateKey(requestId), stateJson, ttlSeconds, TimeUnit.SECONDS);
    }

    public String getOAuth2State(String requestId) {
        return redisTemplate.opsForValue().get(oauth2StateKey(requestId));
    }

    public void deleteOAuth2State(String requestId) {
        redisTemplate.delete(oauth2StateKey(requestId));
    }

    private String refreshKey(UUID memberId) {
        return jwtPrefix + REFRESH_PREFIX + memberId;
    }

    private String blacklistKey(String token) {
        return jwtPrefix + BLACKLIST_PREFIX + tokenProvider.getTokenId(token);
    }

    private String oauth2StateKey(String requestId) {
        return jwtPrefix + OAUTH2_STATE_PREFIX + requestId;
    }

    private String suspendedKey(UUID memberId) {
        return jwtPrefix + SUSPENDED_PREFIX + memberId;
    }
}

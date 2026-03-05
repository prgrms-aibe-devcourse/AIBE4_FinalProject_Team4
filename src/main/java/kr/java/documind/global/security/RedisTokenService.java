package kr.java.documind.global.security;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import kr.java.documind.global.security.jwt.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisTokenService {
    private static final String REFRESH_PREFIX = "refresh:";
    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String OAUTH2_STATE_PREFIX = "oauth2_state:";

    private final StringRedisTemplate redisTemplate;
    private final TokenProvider tokenProvider;

    public void saveRefreshToken(UUID memberId, String refreshToken, long ttlSeconds) {
        redisTemplate
                .opsForValue()
                .set(refreshKey(memberId), refreshToken, ttlSeconds, TimeUnit.SECONDS);
    }

    public String getRefreshToken(UUID memberId) {
        return redisTemplate.opsForValue().get(refreshKey(memberId));
    }

    /**
     * Refresh Token을 원자적으로 읽고 즉시 삭제한다 (Redis GETDEL).
     *
     * <p>GET + DELETE를 하나의 원자 연산으로 처리하므로, 동일한 토큰으로 동시에 들어온 두 요청 중
     * 오직 첫 번째 요청만 토큰 값을 얻고, 이후 요청은 null을 반환받아 거부된다.
     *
     * @return 저장된 Refresh Token 문자열, 없으면 null
     */
    public String consumeRefreshToken(UUID memberId) {
        return redisTemplate.opsForValue().getAndDelete(refreshKey(memberId));
    }

    public void deleteRefreshToken(UUID memberId) {
        redisTemplate.delete(refreshKey(memberId));
    }

    public void addToBlacklist(String accessToken, long ttlMillis) {
        if (ttlMillis <= 0) {
            return; // 이미 만료된 토큰은 블랙리스트 등록 불필요
        }
        long ttlSeconds = Math.max(1L, ttlMillis / 1000);
        redisTemplate
                .opsForValue()
                .set(blacklistKey(accessToken), "1", ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean isBlacklisted(String accessToken) {
        return redisTemplate.hasKey(blacklistKey(accessToken));
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
        return REFRESH_PREFIX + memberId;
    }

    private String blacklistKey(String token) {
        return BLACKLIST_PREFIX + tokenProvider.getTokenId(token);
    }

    private String oauth2StateKey(String requestId) {
        return OAUTH2_STATE_PREFIX + requestId;
    }
}

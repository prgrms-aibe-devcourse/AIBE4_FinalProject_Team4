package kr.java.documind.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.lenient;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import kr.java.documind.global.security.jwt.TokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisTokenService 단위 테스트")
class RedisTokenServiceTest {

    @InjectMocks private RedisTokenService redisTokenService;

    @Mock private StringRedisTemplate redisTemplate;

    @Mock private TokenProvider tokenProvider;

    @Mock private RedisScript<Long> rotateRefreshTokenScript;

    @SuppressWarnings("unchecked")
    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final String JWT_PREFIX = "jwt:";

    @BeforeEach
    void setUp() {
        // Given
        ReflectionTestUtils.setField(redisTokenService, "jwtPrefix", JWT_PREFIX);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("saveRefreshToken() 단위 테스트")
    class SaveRefreshTokenTest {

        @Test
        @DisplayName("리프레시 토큰 저장: refreshToken을 TTL과 함께 Redis에 저장")
        void saveRefreshToken_refreshToken을저장하면_TTL과함께Redis에저장한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String refreshToken = "sample.refresh.token";
            long ttlSeconds = 604800L;
            String expectedKey = JWT_PREFIX + "refresh:" + memberId;

            // When
            redisTokenService.saveRefreshToken(memberId, refreshToken, ttlSeconds);

            // Then
            then(valueOperations)
                    .should()
                    .set(expectedKey, refreshToken, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    @Nested
    @DisplayName("getRefreshToken() 단위 테스트")
    class GetRefreshTokenTest {

        @Test
        @DisplayName("리프레시 토큰 조회: 저장된 refreshToken이 있으면 정상 반환")
        void getRefreshToken_저장된RefreshToken이있으면_정상반환한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String storedToken = "stored.refresh.token";
            String expectedKey = JWT_PREFIX + "refresh:" + memberId;
            given(valueOperations.get(expectedKey)).willReturn(storedToken);

            // When
            String result = redisTokenService.getRefreshToken(memberId);

            // Then
            assertThat(result).isEqualTo(storedToken);
        }

        @Test
        @DisplayName("리프레시 토큰 조회: TTL이 만료된 키면 null 반환")
        void getRefreshToken_TTL이만료된키면_null을반환한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String expectedKey = JWT_PREFIX + "refresh:" + memberId;
            given(valueOperations.get(expectedKey)).willReturn(null);

            // When
            String result = redisTokenService.getRefreshToken(memberId);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("rotateRefreshToken() 단위 테스트")
    class RotateRefreshTokenTest {

        @Test
        @DisplayName("리프레시 토큰 교체: Redis 스크립트 실행 결과가 1이면 true 반환")
        void rotateRefreshToken_스크립트실행결과가1이면_true를반환한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String oldRefreshToken = "old.refresh.token";
            String newRefreshToken = "new.refresh.token";
            long ttlSeconds = 604800L;
            String expectedKey = JWT_PREFIX + "refresh:" + memberId;

            given(
                            redisTemplate.execute(
                                    rotateRefreshTokenScript,
                                    Collections.singletonList(expectedKey),
                                    oldRefreshToken,
                                    newRefreshToken,
                                    String.valueOf(ttlSeconds)))
                    .willReturn(1L);

            // When
            boolean result =
                    redisTokenService.rotateRefreshToken(
                            memberId, oldRefreshToken, newRefreshToken, ttlSeconds);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("리프레시 토큰 교체: Redis 스크립트 실행 결과가 0이면 false 반환")
        void rotateRefreshToken_스크립트실행결과가0이면_false를반환한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String oldRefreshToken = "old.refresh.token";
            String newRefreshToken = "new.refresh.token";
            long ttlSeconds = 604800L;
            String expectedKey = JWT_PREFIX + "refresh:" + memberId;

            given(
                            redisTemplate.execute(
                                    rotateRefreshTokenScript,
                                    Collections.singletonList(expectedKey),
                                    oldRefreshToken,
                                    newRefreshToken,
                                    String.valueOf(ttlSeconds)))
                    .willReturn(0L);

            // When
            boolean result =
                    redisTokenService.rotateRefreshToken(
                            memberId, oldRefreshToken, newRefreshToken, ttlSeconds);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("리프레시 토큰 교체: Redis 스크립트 실행 결과가 null이면 false 반환")
        void rotateRefreshToken_스크립트실행결과가Null이면_false를반환한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String oldRefreshToken = "old.refresh.token";
            String newRefreshToken = "new.refresh.token";
            long ttlSeconds = 604800L;
            String expectedKey = JWT_PREFIX + "refresh:" + memberId;

            given(
                            redisTemplate.execute(
                                    rotateRefreshTokenScript,
                                    Collections.singletonList(expectedKey),
                                    oldRefreshToken,
                                    newRefreshToken,
                                    String.valueOf(ttlSeconds)))
                    .willReturn(null);

            // When
            boolean result =
                    redisTokenService.rotateRefreshToken(
                            memberId, oldRefreshToken, newRefreshToken, ttlSeconds);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteRefreshToken() 단위 테스트")
    class DeleteRefreshTokenTest {

        @Test
        @DisplayName("리프레시 토큰 삭제: refreshToken 키를 삭제")
        void deleteRefreshToken_refreshToken키를삭제한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String expectedKey = JWT_PREFIX + "refresh:" + memberId;

            // When
            redisTokenService.deleteRefreshToken(memberId);

            // Then
            then(redisTemplate).should().delete(expectedKey);
        }
    }

    @Nested
    @DisplayName("addToBlacklist() 단위 테스트")
    class AddToBlacklistTest {

        @Test
        @DisplayName("블랙리스트 등록: 유효한 TTL이면 tokenId 기반 키로 등록")
        void addToBlacklist_유효한TTL이면_tokenId기반키로등록한다() {
            // Given
            String accessToken = "valid.access.token";
            long ttlMillis = 60_000L;
            String tokenId = "jti-uuid-value";
            String expectedKey = JWT_PREFIX + "blacklist:" + tokenId;

            given(tokenProvider.getTokenId(accessToken)).willReturn(tokenId);

            // When
            redisTokenService.addToBlacklist(accessToken, ttlMillis);

            // Then
            then(valueOperations).should().set(expectedKey, "1", 60L, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("블랙리스트 등록: ttlMillis가 1이면 최소 1초 TTL로 등록")
        void addToBlacklist_ttlMillis가1이면_최소1초TTL로등록한다() {
            // Given
            String accessToken = "almost.expired.token";
            long ttlMillis = 1L;
            String tokenId = "jti-short-ttl";
            String expectedKey = JWT_PREFIX + "blacklist:" + tokenId;

            given(tokenProvider.getTokenId(accessToken)).willReturn(tokenId);

            // When
            redisTokenService.addToBlacklist(accessToken, ttlMillis);

            // Then
            then(valueOperations).should().set(expectedKey, "1", 1L, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("블랙리스트 등록: ttlMillis가 1001이면 올림 처리되어 2초 TTL로 등록")
        void addToBlacklist_ttlMillis가1001이면_올림처리되어2초TTL로등록한다() {
            // Given
            String accessToken = "short.ttl.token";
            long ttlMillis = 1001L;
            String tokenId = "jti-round-up";
            String expectedKey = JWT_PREFIX + "blacklist:" + tokenId;

            given(tokenProvider.getTokenId(accessToken)).willReturn(tokenId);

            // When
            redisTokenService.addToBlacklist(accessToken, ttlMillis);

            // Then
            then(valueOperations).should().set(expectedKey, "1", 2L, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("블랙리스트 등록: ttlMillis가 0이면 등록을 건너뛴다")
        void addToBlacklist_ttlMillis가0이면_등록을건너뛴다() {
            // Given
            String accessToken = "already.expired.token";

            // When
            redisTokenService.addToBlacklist(accessToken, 0L);

            // Then
            then(tokenProvider).should(never()).getTokenId(anyString());
            then(valueOperations).should(never()).set(anyString(), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("블랙리스트 등록: ttlMillis가 음수이면 등록을 건너뛴다")
        void addToBlacklist_ttlMillis가음수이면_등록을건너뛴다() {
            // Given
            String accessToken = "expired.access.token";

            // When
            redisTokenService.addToBlacklist(accessToken, -1000L);

            // Then
            then(tokenProvider).should(never()).getTokenId(anyString());
            then(valueOperations).should(never()).set(anyString(), anyString(), anyLong(), any());
        }
    }

    @Nested
    @DisplayName("isBlacklisted() 단위 테스트")
    class IsBlacklistedTest {

        @Test
        @DisplayName("블랙리스트 조회: 등록된 토큰이면 true 반환")
        void isBlacklisted_등록된토큰이면_true를반환한다() {
            // Given
            String accessToken = "blacklisted.access.token";
            String tokenId = "blacklisted-jti";
            String expectedKey = JWT_PREFIX + "blacklist:" + tokenId;

            given(tokenProvider.getTokenId(accessToken)).willReturn(tokenId);
            given(redisTemplate.hasKey(expectedKey)).willReturn(true);

            // When
            boolean result = redisTokenService.isBlacklisted(accessToken);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("블랙리스트 조회: 등록되지 않은 토큰이면 false 반환")
        void isBlacklisted_등록되지않은토큰이면_false를반환한다() {
            // Given
            String accessToken = "normal.access.token";
            String tokenId = "normal-jti";
            String expectedKey = JWT_PREFIX + "blacklist:" + tokenId;

            given(tokenProvider.getTokenId(accessToken)).willReturn(tokenId);
            given(redisTemplate.hasKey(expectedKey)).willReturn(false);

            // When
            boolean result = redisTokenService.isBlacklisted(accessToken);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("revokeAllTokensByMember() 단위 테스트")
    class RevokeAllTokensByMemberTest {

        @Test
        @DisplayName("토큰 전체 무효화: refreshToken 삭제 후 suspended 마커를 등록")
        void revokeAllTokensByMember_refreshToken을삭제한후_suspended마커를등록한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            long accessTokenTtlSeconds = 1800L;
            String expectedRefreshKey = JWT_PREFIX + "refresh:" + memberId;
            String expectedSuspendedKey = JWT_PREFIX + "suspended:" + memberId;

            // When
            redisTokenService.revokeAllTokensByMember(memberId, accessTokenTtlSeconds);

            // Then
            then(redisTemplate).should().delete(expectedRefreshKey);
            then(valueOperations)
                    .should()
                    .set(expectedSuspendedKey, "1", accessTokenTtlSeconds, TimeUnit.SECONDS);
        }
    }

    @Nested
    @DisplayName("isMemberSuspended() 단위 테스트")
    class IsMemberSuspendedTest {

        @Test
        @DisplayName("정지 상태 조회: suspended 마커가 있으면 true 반환")
        void isMemberSuspended_suspended마커가있으면_true를반환한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String expectedKey = JWT_PREFIX + "suspended:" + memberId;
            given(redisTemplate.hasKey(expectedKey)).willReturn(true);

            // When
            boolean result = redisTokenService.isMemberSuspended(memberId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("정지 상태 조회: suspended 마커가 없으면 false 반환")
        void isMemberSuspended_suspended마커가없으면_false를반환한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String expectedKey = JWT_PREFIX + "suspended:" + memberId;
            given(redisTemplate.hasKey(expectedKey)).willReturn(false);

            // When
            boolean result = redisTokenService.isMemberSuspended(memberId);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("clearSuspension() 단위 테스트")
    class ClearSuspensionTest {

        @Test
        @DisplayName("정지 상태 해제: suspended 마커 키를 삭제")
        void clearSuspension_suspended마커키를삭제한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String expectedKey = JWT_PREFIX + "suspended:" + memberId;

            // When
            redisTokenService.clearSuspension(memberId);

            // Then
            then(redisTemplate).should().delete(expectedKey);
        }
    }
}

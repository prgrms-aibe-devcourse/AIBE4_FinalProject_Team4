package kr.java.documind.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RedisTokenServiceTest {

    @InjectMocks private RedisTokenService redisTokenService;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private TokenProvider tokenProvider;

    @SuppressWarnings("unchecked")
    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final String JWT_PREFIX = "jwt:";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(redisTokenService, "jwtPrefix", JWT_PREFIX);
        // opsForValue()는 대부분의 테스트에서 사용되므로 lenient로 설정
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("saveRefreshToken()")
    class SaveRefreshToken {

        @Test
        @DisplayName("기능: refreshToken을 Redis에 TTL과 함께 저장")
        void saveRefreshToken_정상호출_TTL포함저장() {
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
    @DisplayName("getRefreshToken()")
    class GetRefreshToken {

        @Test
        @DisplayName("기능: 저장된 refreshToken 조회 → 정상 반환")
        void getRefreshToken_저장된토큰_정상반환() {
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
        @DisplayName("기능: TTL 만료된 키 조회 → null 반환")
        void getRefreshToken_만료된키_null반환() {
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
    @DisplayName("consumeRefreshToken()")
    class ConsumeRefreshToken {

        @Test
        @DisplayName("기능: refreshToken 원자적 소비(getAndDelete) → 반환 후 키 삭제")
        void consumeRefreshToken_정상호출_getAndDelete수행() {
            // Given
            UUID memberId = UUID.randomUUID();
            String storedToken = "stored.refresh.token";
            String expectedKey = JWT_PREFIX + "refresh:" + memberId;
            given(valueOperations.getAndDelete(expectedKey)).willReturn(storedToken);

            // When
            String result = redisTokenService.consumeRefreshToken(memberId);

            // Then
            assertThat(result).isEqualTo(storedToken);
            then(valueOperations).should().getAndDelete(expectedKey);
        }

        @Test
        @DisplayName("예외: Redis 장애 → RuntimeException 전파")
        void consumeRefreshToken_Redis장애_RuntimeException전파() {
            // Given
            UUID memberId = UUID.randomUUID();
            given(valueOperations.getAndDelete(anyString())).willThrow(RuntimeException.class);

            // When & Then
            assertThatThrownBy(() -> redisTokenService.consumeRefreshToken(memberId))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("deleteRefreshToken()")
    class DeleteRefreshToken {

        @Test
        @DisplayName("기능: refreshToken 키 삭제")
        void deleteRefreshToken_정상호출_키삭제수행() {
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
    @DisplayName("addToBlacklist()")
    class AddToBlacklist {

        @Test
        @DisplayName("기능: 유효한 TTL → tokenId 기반 키로 블랙리스트 등록")
        void addToBlacklist_유효한TTL_블랙리스트등록() {
            // Given
            String accessToken = "valid.access.token";
            long ttlMillis = 60_000L; // 60초
            String tokenId = "jti-uuid-value";
            String expectedKey = JWT_PREFIX + "blacklist:" + tokenId;
            given(tokenProvider.getTokenId(accessToken)).willReturn(tokenId);

            // When
            redisTokenService.addToBlacklist(accessToken, ttlMillis);

            // Then
            then(valueOperations).should().set(expectedKey, "1", 60L, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("경계값: ttlMillis가 1ms → Math.max(1, 0) = 1초 TTL로 등록")
        void addToBlacklist_1ms_최소1초TTL로등록() {
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
        @DisplayName("경계값: ttlMillis가 0 → 등록 스킵")
        void addToBlacklist_0ms_등록스킵() {
            // Given
            String accessToken = "already.expired.token";

            // When
            redisTokenService.addToBlacklist(accessToken, 0L);

            // Then
            then(tokenProvider).should(never()).getTokenId(anyString());
            then(valueOperations).should(never()).set(anyString(), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("경계값: ttlMillis가 음수 → 등록 스킵")
        void addToBlacklist_음수ttl_등록스킵() {
            // Given
            String accessToken = "expired.access.token";

            // When
            redisTokenService.addToBlacklist(accessToken, -1000L);

            // Then
            then(tokenProvider).should(never()).getTokenId(anyString());
        }
    }

    @Nested
    @DisplayName("isBlacklisted()")
    class IsBlacklisted {

        @Test
        @DisplayName("기능: 블랙리스트에 등록된 토큰 → true 반환")
        void isBlacklisted_등록된토큰_true반환() {
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
        @DisplayName("기능: 블랙리스트에 없는 토큰 → false 반환")
        void isBlacklisted_미등록토큰_false반환() {
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
    @DisplayName("revokeAllTokensByMember()")
    class RevokeAllTokensByMember {

        @Test
        @DisplayName("기능: refreshToken 삭제 + suspended 마커 등록으로 즉시 차단")
        void revokeAllTokensByMember_정상호출_refresh삭제및suspended등록() {
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
    @DisplayName("isMemberSuspended()")
    class IsMemberSuspended {

        @Test
        @DisplayName("기능: suspended 마커 존재 → true 반환")
        void isMemberSuspended_마커존재_true반환() {
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
        @DisplayName("기능: suspended 마커 없음(TTL 만료 포함) → false 반환")
        void isMemberSuspended_마커없음_false반환() {
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
    @DisplayName("clearSuspension()")
    class ClearSuspension {

        @Test
        @DisplayName("기능: suspended 마커 삭제")
        void clearSuspension_정상호출_suspended키삭제() {
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

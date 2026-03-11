package kr.java.documind.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.UUID;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.global.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenProviderTest {

    // HMAC-SHA256 최소 256bit(32byte) 이상의 테스트용 비밀키
    private static final String TEST_SECRET =
            Base64.getEncoder().encodeToString("abcdefghijklmnopqrstuvwxyz123456".getBytes());
    private static final long ACCESS_TTL_SECONDS = 3600L;
    private static final long REFRESH_TTL_SECONDS = 604800L;

    private TokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties props =
                new JwtProperties(
                        TEST_SECRET,
                        ACCESS_TTL_SECONDS,
                        REFRESH_TTL_SECONDS,
                        "access_token",
                        "refresh_token",
                        false);
        tokenProvider = new TokenProvider(props);
        tokenProvider.init(); // @PostConstruct 수동 호출
    }

    /** TTL을 음수로 설정하여 만료 시각이 과거인 토큰을 즉시 생성한다. Thread.sleep 없이 만료 토큰을 얻기 위한 헬퍼. */
    private String buildExpiredAccessToken(UUID memberId, GlobalRole role) {
        JwtProperties expiredProps =
                new JwtProperties(TEST_SECRET, -1L, -1L, "access_token", "refresh_token", false);
        TokenProvider expiredProvider = new TokenProvider(expiredProps);
        expiredProvider.init();
        return expiredProvider.generateAccessToken(memberId, role);
    }

    private String buildExpiredRefreshToken(UUID memberId, GlobalRole role) {
        JwtProperties expiredProps =
                new JwtProperties(TEST_SECRET, -1L, -1L, "access_token", "refresh_token", false);
        TokenProvider expiredProvider = new TokenProvider(expiredProps);
        expiredProvider.init();
        return expiredProvider.generateRefreshToken(memberId, role);
    }

    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateAccessToken {

        @Test
        @DisplayName("기능: 정상 payload → 유효한 토큰 생성")
        void generateAccessToken_정상payload_유효한토큰생성() {
            // Given
            UUID memberId = UUID.randomUUID();

            // When
            String token = tokenProvider.generateAccessToken(memberId, GlobalRole.CEO);

            // Then
            assertThat(token).isNotNull().isNotBlank();
            assertThat(tokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("기능: 생성된 토큰에서 memberId 정확히 추출")
        void generateAccessToken_생성된토큰에서memberId정확히추출() {
            // Given
            UUID memberId = UUID.randomUUID();

            // When
            String token = tokenProvider.generateAccessToken(memberId, GlobalRole.EMPLOYEE);

            // Then
            assertThat(tokenProvider.getMemberId(token)).isEqualTo(memberId);
        }

        @Test
        @DisplayName("기능: 생성된 토큰에서 GlobalRole 정확히 추출")
        void generateAccessToken_생성된토큰에서GlobalRole정확히추출() {
            // Given
            UUID memberId = UUID.randomUUID();

            // When
            String token = tokenProvider.generateAccessToken(memberId, GlobalRole.ADMIN);

            // Then
            assertThat(tokenProvider.getGlobalRole(token)).isEqualTo(GlobalRole.ADMIN);
        }

        @Test
        @DisplayName("기능: access 토큰 타입 확인 → isAccessToken() true 반환")
        void generateAccessToken_isAccessToken_true반환() {
            // Given
            String token =
                    tokenProvider.generateAccessToken(UUID.randomUUID(), GlobalRole.EMPLOYEE);

            // When & Then
            assertThat(tokenProvider.isAccessToken(token)).isTrue();
        }
    }

    @Nested
    @DisplayName("generateRefreshToken()")
    class GenerateRefreshToken {

        @Test
        @DisplayName("기능: refresh 토큰은 isAccessToken() false 반환")
        void generateRefreshToken_isAccessToken_false반환() {
            // Given
            String token =
                    tokenProvider.generateRefreshToken(UUID.randomUUID(), GlobalRole.EMPLOYEE);

            // When & Then
            assertThat(tokenProvider.isAccessToken(token)).isFalse();
        }

        @Test
        @DisplayName("기능: refresh 토큰에서 memberId와 role 정확히 추출")
        void generateRefreshToken_memberId와role정확히추출() {
            // Given
            UUID memberId = UUID.randomUUID();

            // When
            String token = tokenProvider.generateRefreshToken(memberId, GlobalRole.CEO);

            // Then
            assertThat(tokenProvider.getMemberId(token)).isEqualTo(memberId);
            assertThat(tokenProvider.getGlobalRole(token)).isEqualTo(GlobalRole.CEO);
        }
    }

    @Nested
    @DisplayName("validateToken()")
    class ValidateToken {

        @Test
        @DisplayName("기능: 유효한 토큰 → true 반환")
        void validateToken_유효한토큰_true반환() {
            // Given
            String token =
                    tokenProvider.generateAccessToken(UUID.randomUUID(), GlobalRole.EMPLOYEE);

            // When & Then
            assertThat(tokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("예외: 만료된 토큰 → false 반환")
        void validateToken_만료된토큰_false반환() {
            // Given
            String expiredToken = buildExpiredAccessToken(UUID.randomUUID(), GlobalRole.EMPLOYEE);

            // When & Then
            assertThat(tokenProvider.validateToken(expiredToken)).isFalse();
        }

        @Test
        @DisplayName("예외: 변조된 시그니처 → false 반환")
        void validateToken_변조된시그니처_false반환() {
            // Given
            String tamperedToken = "eyJhbGciOiJIUzI1NiJ9.tampered-payload.invalid-signature";

            // When & Then
            assertThat(tokenProvider.validateToken(tamperedToken)).isFalse();
        }

        @Test
        @DisplayName("예외: 빈 문자열 → false 반환")
        void validateToken_빈문자열_false반환() {
            // When & Then
            assertThat(tokenProvider.validateToken("")).isFalse();
        }
    }

    @Nested
    @DisplayName("getMemberIdFromExpiredToken()")
    class GetMemberIdFromExpiredToken {

        @Test
        @DisplayName("기능: 만료된 토큰에서 memberId 정상 추출")
        void getMemberIdFromExpiredToken_만료된토큰_memberId추출성공() {
            // Given
            UUID memberId = UUID.randomUUID();
            String expiredToken = buildExpiredAccessToken(memberId, GlobalRole.EMPLOYEE);

            // When
            UUID extracted = tokenProvider.getMemberIdFromExpiredToken(expiredToken);

            // Then
            assertThat(extracted).isEqualTo(memberId);
        }

        @Test
        @DisplayName("기능: 유효한 토큰에서도 memberId 정상 추출")
        void getMemberIdFromExpiredToken_유효한토큰_memberId추출성공() {
            // Given
            UUID memberId = UUID.randomUUID();
            String validToken = tokenProvider.generateAccessToken(memberId, GlobalRole.CEO);

            // When
            UUID extracted = tokenProvider.getMemberIdFromExpiredToken(validToken);

            // Then
            assertThat(extracted).isEqualTo(memberId);
        }
    }

    @Nested
    @DisplayName("getRemainingMillis()")
    class GetRemainingMillis {

        @Test
        @DisplayName("기능: 유효한 토큰 → 양수 remaining 반환")
        void getRemainingMillis_유효한토큰_양수반환() {
            // Given
            String token =
                    tokenProvider.generateAccessToken(UUID.randomUUID(), GlobalRole.EMPLOYEE);

            // When
            long remaining = tokenProvider.getRemainingMillis(token);

            // Then
            assertThat(remaining).isPositive();
        }

        @Test
        @DisplayName("기능: 만료된 토큰 → 음수 remaining 반환")
        void getRemainingMillis_만료된토큰_음수반환() {
            // Given
            String expiredToken = buildExpiredAccessToken(UUID.randomUUID(), GlobalRole.EMPLOYEE);

            // When
            long remaining = tokenProvider.getRemainingMillis(expiredToken);

            // Then
            assertThat(remaining).isNegative();
        }
    }

    @Nested
    @DisplayName("getTokenId()")
    class GetTokenId {

        @Test
        @DisplayName("기능: 유효한 토큰에서 고유 jti 추출")
        void getTokenId_유효한토큰_고유jti추출() {
            // Given
            String tokenA =
                    tokenProvider.generateAccessToken(UUID.randomUUID(), GlobalRole.EMPLOYEE);
            String tokenB =
                    tokenProvider.generateAccessToken(UUID.randomUUID(), GlobalRole.EMPLOYEE);

            // When
            String idA = tokenProvider.getTokenId(tokenA);
            String idB = tokenProvider.getTokenId(tokenB);

            // Then
            assertThat(idA).isNotNull().isNotEqualTo(idB);
        }

        @Test
        @DisplayName("기능: 만료된 토큰에서도 jti 추출 가능")
        void getTokenId_만료된토큰에서도jti추출() {
            // Given
            UUID memberId = UUID.randomUUID();
            String expiredToken = buildExpiredAccessToken(memberId, GlobalRole.EMPLOYEE);

            // When
            String tokenId = tokenProvider.getTokenId(expiredToken);

            // Then
            assertThat(tokenId).isNotNull().isNotBlank();
        }
    }
}

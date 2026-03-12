package kr.java.documind.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.UUID;

import kr.java.documind.domain.auth.exception.InvalidTokenException;
import kr.java.documind.domain.auth.exception.TokenExpiredException;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.global.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TokenProvider 단위 테스트")
class TokenProviderTest {

    private static final String TEST_SECRET =
        Base64.getEncoder().encodeToString("abcdefghijklmnopqrstuvwxyz123456".getBytes());
    private static final long ACCESS_TTL_SECONDS = 3600L;
    private static final long REFRESH_TTL_SECONDS = 604800L;

    private TokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(
            TEST_SECRET,
            ACCESS_TTL_SECONDS,
            REFRESH_TTL_SECONDS,
            "access_token",
            "refresh_token",
            false);

        tokenProvider = new TokenProvider(props);
        tokenProvider.init();
    }

    private String createExpiredAccessToken(UUID memberId, GlobalRole role) {
        JwtProperties expiredProps = new JwtProperties(
            TEST_SECRET,
            -1L,
            -1L,
            "access_token",
            "refresh_token",
            false);

        TokenProvider expiredProvider = new TokenProvider(expiredProps);
        expiredProvider.init();

        return expiredProvider.generateAccessToken(memberId, role);
    }

    private String createExpiredRefreshToken(UUID memberId, GlobalRole role) {
        JwtProperties expiredProps = new JwtProperties(
            TEST_SECRET,
            -1L,
            -1L,
            "access_token",
            "refresh_token",
            false);

        TokenProvider expiredProvider = new TokenProvider(expiredProps);
        expiredProvider.init();

        return expiredProvider.generateRefreshToken(memberId, role);
    }

    @Nested
    @DisplayName("generateAccessToken() 단위 테스트")
    class GenerateAccessTokenTest {

        @Test
        @DisplayName("액세스 토큰 생성: 정상 payload이면 유효한 액세스 토큰 생성")
        void generateAccessToken_정상Payload이면_유효한액세스토큰을생성한다() {
            // Given
            UUID memberId = UUID.randomUUID();

            // When
            String token = tokenProvider.generateAccessToken(memberId, GlobalRole.CEO);

            // Then
            assertThat(token).isNotNull().isNotBlank();
            assertThatCode(() -> tokenProvider.validateAccessToken(token)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("액세스 토큰 생성: 생성된 토큰에서 memberId를 정확히 추출")
        void generateAccessToken_생성된토큰이면_memberId를정확히추출한다() {
            // Given
            UUID memberId = UUID.randomUUID();

            // When
            String token = tokenProvider.generateAccessToken(memberId, GlobalRole.EMPLOYEE);

            // Then
            assertThat(tokenProvider.getMemberId(token)).isEqualTo(memberId);
        }

        @Test
        @DisplayName("액세스 토큰 생성: 생성된 토큰에서 GlobalRole을 정확히 추출")
        void generateAccessToken_생성된토큰이면_GlobalRole을정확히추출한다() {
            // Given
            UUID memberId = UUID.randomUUID();

            // When
            String token = tokenProvider.generateAccessToken(memberId, GlobalRole.ADMIN);

            // Then
            assertThat(tokenProvider.getGlobalRole(token)).isEqualTo(GlobalRole.ADMIN);
        }

        @Test
        @DisplayName("액세스 토큰 생성: access 토큰이면 isAccessToken은 true를 반환")
        void generateAccessToken_access토큰이면_isAccessToken은True를반환한다() {
            // Given
            String token = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When
            boolean result = tokenProvider.isAccessToken(token);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("액세스 토큰 생성: access 토큰이면 isRefreshToken은 false를 반환")
        void generateAccessToken_access토큰이면_isRefreshToken은False를반환한다() {
            // Given
            String token = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When
            boolean result = tokenProvider.isRefreshToken(token);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("generateRefreshToken() 단위 테스트")
    class GenerateRefreshTokenTest {

        @Test
        @DisplayName("리프레시 토큰 생성: 정상 payload이면 유효한 리프레시 토큰 생성")
        void generateRefreshToken_정상Payload이면_유효한리프레시토큰을생성한다() {
            // Given
            UUID memberId = UUID.randomUUID();

            // When
            String token = tokenProvider.generateRefreshToken(memberId, GlobalRole.EMPLOYEE);

            // Then
            assertThat(token).isNotNull().isNotBlank();
            assertThatCode(() -> tokenProvider.validateRefreshToken(token)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("리프레시 토큰 생성: 생성된 토큰에서 memberId와 role을 정확히 추출")
        void generateRefreshToken_생성된토큰이면_memberId와Role을정확히추출한다() {
            // Given
            UUID memberId = UUID.randomUUID();

            // When
            String token = tokenProvider.generateRefreshToken(memberId, GlobalRole.CEO);

            // Then
            assertThat(tokenProvider.getMemberId(token)).isEqualTo(memberId);
            assertThat(tokenProvider.getGlobalRole(token)).isEqualTo(GlobalRole.CEO);
        }

        @Test
        @DisplayName("리프레시 토큰 생성: refresh 토큰이면 isAccessToken은 false를 반환")
        void generateRefreshToken_refresh토큰이면_isAccessToken은False를반환한다() {
            // Given
            String token = tokenProvider.generateRefreshToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When
            boolean result = tokenProvider.isAccessToken(token);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("리프레시 토큰 생성: refresh 토큰이면 isRefreshToken은 true를 반환")
        void generateRefreshToken_refresh토큰이면_isRefreshToken은True를반환한다() {
            // Given
            String token = tokenProvider.generateRefreshToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When
            boolean result = tokenProvider.isRefreshToken(token);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("validateAccessToken() 단위 테스트")
    class ValidateAccessTokenTest {

        @Test
        @DisplayName("액세스 토큰 검증: 유효한 액세스 토큰이면 예외가 발생하지 않는다")
        void validateAccessToken_유효한액세스토큰이면_예외가발생하지않는다() {
            // Given
            String token = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When & Then
            assertThatCode(() -> tokenProvider.validateAccessToken(token))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("액세스 토큰 검증: 만료된 액세스 토큰이면 TokenExpiredException 발생")
        void validateAccessToken_만료된액세스토큰이면_TokenExpiredException이발생한다() {
            // Given
            String expiredToken = createExpiredAccessToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When & Then
            assertThatThrownBy(() -> tokenProvider.validateAccessToken(expiredToken))
                .isInstanceOf(TokenExpiredException.class)
                .hasMessageContaining("만료");
        }

        @Test
        @DisplayName("액세스 토큰 검증: 변조된 토큰이면 InvalidTokenException 발생")
        void validateAccessToken_변조된토큰이면_InvalidTokenException이발생한다() {
            // Given
            String tamperedToken = "eyJhbGciOiJIUzI1NiJ9.tampered-payload.invalid-signature";

            // When & Then
            assertThatThrownBy(() -> tokenProvider.validateAccessToken(tamperedToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("유효하지 않은 토큰");
        }

        @Test
        @DisplayName("액세스 토큰 검증: 빈 문자열이면 InvalidTokenException 발생")
        void validateAccessToken_빈문자열이면_InvalidTokenException이발생한다() {
            // Given
            String blankToken = "";

            // When & Then
            assertThatThrownBy(() -> tokenProvider.validateAccessToken(blankToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("유효하지 않은 토큰");
        }

        @Test
        @DisplayName("액세스 토큰 검증: refresh 토큰이면 InvalidTokenException 발생")
        void validateAccessToken_refresh토큰이면_InvalidTokenException이발생한다() {
            // Given
            String refreshToken = tokenProvider.generateRefreshToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When & Then
            assertThatThrownBy(() -> tokenProvider.validateAccessToken(refreshToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("유효하지 않은 토큰");
        }
    }

    @Nested
    @DisplayName("validateRefreshToken() 단위 테스트")
    class ValidateRefreshTokenTest {

        @Test
        @DisplayName("리프레시 토큰 검증: 유효한 리프레시 토큰이면 예외가 발생하지 않는다")
        void validateRefreshToken_유효한리프레시토큰이면_예외가발생하지않는다() {
            // Given
            String token = tokenProvider.generateRefreshToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When & Then
            assertThatCode(() -> tokenProvider.validateRefreshToken(token))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("리프레시 토큰 검증: 만료된 리프레시 토큰이면 TokenExpiredException 발생")
        void validateRefreshToken_만료된리프레시토큰이면_TokenExpiredException이발생한다() {
            // Given
            String expiredToken = createExpiredRefreshToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When & Then
            assertThatThrownBy(() -> tokenProvider.validateRefreshToken(expiredToken))
                .isInstanceOf(TokenExpiredException.class)
                .hasMessageContaining("만료");
        }

        @Test
        @DisplayName("리프레시 토큰 검증: access 토큰이면 InvalidTokenException 발생")
        void validateRefreshToken_access토큰이면_InvalidTokenException이발생한다() {
            // Given
            String accessToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When & Then
            assertThatThrownBy(() -> tokenProvider.validateRefreshToken(accessToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("유효하지 않은 토큰");
        }
    }

    @Nested
    @DisplayName("getMemberIdAllowExpired() 단위 테스트")
    class GetMemberIdAllowExpiredTest {

        @Test
        @DisplayName("memberId 추출: 만료된 토큰이면 memberId를 정상 추출")
        void getMemberIdAllowExpired_만료된토큰이면_memberId를정상추출한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String expiredToken = createExpiredAccessToken(memberId, GlobalRole.EMPLOYEE);

            // When
            UUID extractedMemberId = tokenProvider.getMemberIdAllowExpired(expiredToken);

            // Then
            assertThat(extractedMemberId).isEqualTo(memberId);
        }

        @Test
        @DisplayName("memberId 추출: 유효한 토큰이면 memberId를 정상 추출")
        void getMemberIdAllowExpired_유효한토큰이면_memberId를정상추출한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String validToken = tokenProvider.generateAccessToken(memberId, GlobalRole.CEO);

            // When
            UUID extractedMemberId = tokenProvider.getMemberIdAllowExpired(validToken);

            // Then
            assertThat(extractedMemberId).isEqualTo(memberId);
        }

        @Test
        @DisplayName("memberId 추출: 변조된 토큰이면 InvalidTokenException 발생")
        void getMemberIdAllowExpired_변조된토큰이면_InvalidTokenException이발생한다() {
            // Given
            String tamperedToken = "invalid.token.value";

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getMemberIdAllowExpired(tamperedToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("유효하지 않은 토큰");
        }
    }

    @Nested
    @DisplayName("getRemainingMillis() 단위 테스트")
    class GetRemainingMillisTest {

        @Test
        @DisplayName("남은 시간 조회: 유효한 토큰이면 양수 remainingMillis를 반환")
        void getRemainingMillis_유효한토큰이면_양수RemainingMillis를반환한다() {
            // Given
            String token = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When
            long remainingMillis = tokenProvider.getRemainingMillis(token);

            // Then
            assertThat(remainingMillis).isPositive();
        }

        @Test
        @DisplayName("남은 시간 조회: 만료된 토큰이면 음수 remainingMillis를 반환")
        void getRemainingMillis_만료된토큰이면_음수RemainingMillis를반환한다() {
            // Given
            String expiredToken = createExpiredAccessToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When
            long remainingMillis = tokenProvider.getRemainingMillis(expiredToken);

            // Then
            assertThat(remainingMillis).isNegative();
        }
    }

    @Nested
    @DisplayName("getTokenId() 단위 테스트")
    class GetTokenIdTest {

        @Test
        @DisplayName("토큰 ID 조회: 서로 다른 유효한 토큰이면 서로 다른 jti를 반환")
        void getTokenId_서로다른유효한토큰이면_서로다른Jti를반환한다() {
            // Given
            String tokenA = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);
            String tokenB = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                GlobalRole.EMPLOYEE);

            // When
            String tokenIdA = tokenProvider.getTokenId(tokenA);
            String tokenIdB = tokenProvider.getTokenId(tokenB);

            // Then
            assertThat(tokenIdA).isNotNull().isNotBlank();
            assertThat(tokenIdB).isNotNull().isNotBlank();
            assertThat(tokenIdA).isNotEqualTo(tokenIdB);
        }

        @Test
        @DisplayName("토큰 ID 조회: 만료된 토큰이어도 jti를 추출할 수 있다")
        void getTokenId_만료된토큰이어도_jti를추출할수있다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String expiredToken = createExpiredAccessToken(memberId, GlobalRole.EMPLOYEE);

            // When
            String tokenId = tokenProvider.getTokenId(expiredToken);

            // Then
            assertThat(tokenId).isNotNull().isNotBlank();
        }
    }
}

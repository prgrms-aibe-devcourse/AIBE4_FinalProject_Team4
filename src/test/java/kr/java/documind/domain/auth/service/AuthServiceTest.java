package kr.java.documind.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import io.jsonwebtoken.ExpiredJwtException;
import java.util.UUID;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.auth.model.enums.OAuthProvider;
import kr.java.documind.domain.auth.service.AuthService.AuthTokens;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.service.MemberService;
import kr.java.documind.global.config.JwtProperties;
import kr.java.documind.global.exception.ForbiddenException;
import kr.java.documind.global.exception.UnauthorizedException;
import kr.java.documind.global.security.RedisTokenService;
import kr.java.documind.global.security.jwt.TokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks private AuthService authService;

    @Mock private TokenProvider jwtProvider;
    @Mock private RedisTokenService redisTokenService;
    @Mock private MemberService memberService;
    @Mock private JwtProperties jwtProperties;

    @Nested
    @DisplayName("refresh()")
    class Refresh {

        @Test
        @DisplayName("예외: refreshToken이 null → UnauthorizedException 발생")
        void refresh_null리프레시토큰_UnauthorizedException발생() {
            // Given
            // When & Then
            assertThatThrownBy(() -> authService.refresh(null))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Refresh Token이 없습니다");
        }

        @Test
        @DisplayName("예외: 만료된 refreshToken → UnauthorizedException 발생")
        void refresh_만료된리프레시토큰_UnauthorizedException발생() {
            // Given
            String expiredToken = "expired.token";
            doThrow(new ExpiredJwtException(null, null, "만료"))
                    .when(jwtProvider)
                    .validateToken(expiredToken);

            // When & Then
            assertThatThrownBy(() -> authService.refresh(expiredToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("만료되었습니다");
        }

        @Test
        @DisplayName("예외: 변조된 refreshToken → UnauthorizedException 발생")
        void refresh_변조된리프레시토큰_UnauthorizedException발생() {
            // Given
            String invalidToken = "invalid.token";
            doThrow(new UnauthorizedException("유효하지 않은 토큰입니다."))
                    .when(jwtProvider)
                    .validateToken(invalidToken);

            // When & Then
            assertThatThrownBy(() -> authService.refresh(invalidToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("유효하지 않은 Refresh Token");
        }

        @Test
        @DisplayName("예외: Redis에 저장된 토큰과 불일치 → UnauthorizedException 발생 (탈취 방지)")
        void refresh_Redis저장토큰과불일치_UnauthorizedException발생() {
            // Given
            String requestToken = "valid.refresh.token";
            UUID memberId = UUID.randomUUID();
            given(jwtProvider.getMemberId(requestToken)).willReturn(memberId);
            given(jwtProvider.getGlobalRole(requestToken)).willReturn(GlobalRole.EMPLOYEE);
            given(redisTokenService.consumeRefreshToken(memberId)).willReturn("different.token");

            // When & Then
            assertThatThrownBy(() -> authService.refresh(requestToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("유효하지 않은 Refresh Token");
        }

        @Test
        @DisplayName("예외: Redis에 토큰 없음(TTL 만료) → UnauthorizedException 발생")
        void refresh_Redis토큰없음_UnauthorizedException발생() {
            // Given
            String requestToken = "valid.refresh.token";
            UUID memberId = UUID.randomUUID();
            given(jwtProvider.getMemberId(requestToken)).willReturn(memberId);
            given(jwtProvider.getGlobalRole(requestToken)).willReturn(GlobalRole.EMPLOYEE);
            given(redisTokenService.consumeRefreshToken(memberId)).willReturn(null);

            // When & Then
            assertThatThrownBy(() -> authService.refresh(requestToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("유효하지 않은 Refresh Token");
        }

        @Test
        @DisplayName("예외: SUSPENDED 계정 → ForbiddenException 발생")
        void refresh_SUSPENDED계정_ForbiddenException발생() {
            // Given
            String requestToken = "valid.refresh.token";
            UUID memberId = UUID.randomUUID();
            Member suspendedMember = createMember(AccountStatus.SUSPENDED);

            given(jwtProvider.getMemberId(requestToken)).willReturn(memberId);
            given(jwtProvider.getGlobalRole(requestToken)).willReturn(GlobalRole.EMPLOYEE);
            given(redisTokenService.consumeRefreshToken(memberId)).willReturn(requestToken);
            given(memberService.getMemberWithCompany(memberId)).willReturn(suspendedMember);

            // When & Then
            assertThatThrownBy(() -> authService.refresh(requestToken))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("비활성화");
        }

        @Test
        @DisplayName("예외: DELETED 계정 → ForbiddenException 발생")
        void refresh_DELETED계정_ForbiddenException발생() {
            // Given
            String requestToken = "valid.refresh.token";
            UUID memberId = UUID.randomUUID();
            Member deletedMember = createMember(AccountStatus.DELETED);

            given(jwtProvider.getMemberId(requestToken)).willReturn(memberId);
            given(jwtProvider.getGlobalRole(requestToken)).willReturn(GlobalRole.EMPLOYEE);
            given(redisTokenService.consumeRefreshToken(memberId)).willReturn(requestToken);
            given(memberService.getMemberWithCompany(memberId)).willReturn(deletedMember);

            // When & Then
            assertThatThrownBy(() -> authService.refresh(requestToken))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("비활성화");
        }

        @Test
        @DisplayName("기능: ACTIVE 계정 + 유효한 토큰 → 새 토큰 쌍 반환 및 Redis 갱신")
        void refresh_ACTIVE계정유효한토큰_새토큰반환및Redis갱신() {
            // Given
            String oldRefreshToken = "valid.old.refresh.token";
            UUID memberId = UUID.randomUUID();
            Member activeMember = createMember(AccountStatus.ACTIVE);
            String newAccessToken = "new.access.token";
            String newRefreshToken = "new.refresh.token";

            given(jwtProvider.getMemberId(oldRefreshToken)).willReturn(memberId);
            given(jwtProvider.getGlobalRole(oldRefreshToken)).willReturn(GlobalRole.CEO);
            given(redisTokenService.consumeRefreshToken(memberId)).willReturn(oldRefreshToken);
            given(memberService.getMemberWithCompany(memberId)).willReturn(activeMember);
            given(jwtProvider.generateAccessToken(memberId, GlobalRole.CEO))
                    .willReturn(newAccessToken);
            given(jwtProvider.generateRefreshToken(memberId, GlobalRole.CEO))
                    .willReturn(newRefreshToken);
            given(jwtProperties.getRefreshExpirationSeconds()).willReturn(604800L);

            // When
            AuthTokens result = authService.refresh(oldRefreshToken);

            // Then
            assertThat(result.accessToken()).isEqualTo(newAccessToken);
            assertThat(result.refreshToken()).isEqualTo(newRefreshToken);
            then(redisTokenService).should().saveRefreshToken(memberId, newRefreshToken, 604800L);
        }
    }

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("기능: 인증 상태 + 유효한 accessToken → 블랙리스트 등록 및 refreshToken 삭제")
        void logout_인증상태유효한AccessToken_블랙리스트등록및RefreshToken삭제() {
            // Given
            UUID memberId = UUID.randomUUID();
            String accessToken = "valid.access.token";
            String refreshToken = "valid.refresh.token";
            given(jwtProvider.getRemainingMillis(accessToken)).willReturn(60_000L);

            // When
            authService.logout(accessToken, refreshToken, memberId);

            // Then
            then(redisTokenService).should().addToBlacklist(accessToken, 60_000L);
            then(redisTokenService).should().deleteRefreshToken(memberId);
        }

        @Test
        @DisplayName("기능: 인증 상태 + 만료된 accessToken → 블랙리스트 미등록, refreshToken만 삭제")
        void logout_인증상태만료된AccessToken_블랙리스트미등록RefreshToken삭제() {
            // Given
            UUID memberId = UUID.randomUUID();
            String expiredAccessToken = "expired.access.token";
            String refreshToken = "valid.refresh.token";
            given(jwtProvider.getRemainingMillis(expiredAccessToken)).willReturn(-100L);

            // When
            authService.logout(expiredAccessToken, refreshToken, memberId);

            // Then
            then(redisTokenService).should(never()).addToBlacklist(anyString(), anyLong());
            then(redisTokenService).should().deleteRefreshToken(memberId);
        }

        @Test
        @DisplayName("기능: 인증 상태 + accessToken null → refreshToken만 삭제")
        void logout_인증상태AccessTokenNull_refreshToken만삭제() {
            // Given
            UUID memberId = UUID.randomUUID();
            String refreshToken = "valid.refresh.token";

            // When
            authService.logout(null, refreshToken, memberId);

            // Then
            then(jwtProvider).should(never()).getRemainingMillis(anyString());
            then(redisTokenService).should(never()).addToBlacklist(anyString(), anyLong());
            then(redisTokenService).should().deleteRefreshToken(memberId);
        }

        @Test
        @DisplayName("기능: 미인증 상태 + refreshToken 있음 → memberId 추출 후 refreshToken 삭제")
        void logout_미인증상태refreshToken있음_memberId추출후삭제() {
            // Given
            UUID extractedMemberId = UUID.randomUUID();
            String refreshToken = "expired.or.valid.refresh.token";
            given(jwtProvider.getMemberIdFromExpiredToken(refreshToken))
                    .willReturn(extractedMemberId);

            // When
            authService.logout(null, refreshToken, null);

            // Then
            then(redisTokenService).should().deleteRefreshToken(extractedMemberId);
        }

        @Test
        @DisplayName("기능: 미인증 상태 + 모든 토큰 null → 아무 처리 없이 정상 종료")
        void logout_미인증상태모든토큰null_아무처리없음() {
            // Given
            // When
            authService.logout(null, null, null);

            // Then
            then(jwtProvider).should(never()).getMemberIdFromExpiredToken(anyString());
            then(redisTokenService).should(never()).deleteRefreshToken(any(UUID.class));
        }

        @Test
        @DisplayName("기능: 미인증 + refreshToken 파싱 실패 → 예외 미전파, 정상 종료")
        void logout_refreshToken파싱실패_예외미전파정상종료() {
            // Given
            String corruptedToken = "corrupted.token.signature";
            given(jwtProvider.getMemberIdFromExpiredToken(corruptedToken))
                    .willThrow(new RuntimeException("서명 오류"));

            // When (예외가 바깥으로 전파되지 않아야 함)
            authService.logout(null, corruptedToken, null);

            // Then
            then(redisTokenService).should(never()).deleteRefreshToken(any(UUID.class));
        }
    }

    // --- 공통 픽스처 헬퍼 ---

    private Member createMember(AccountStatus status) {
        Member member =
                Member.createByOAuth(
                        "test@example.com",
                        "Test User",
                        "TestNick",
                        OAuthProvider.GOOGLE,
                        "google-provider-id",
                        GlobalRole.EMPLOYEE);
        if (status == AccountStatus.SUSPENDED) {
            member.suspend();
        } else if (status == AccountStatus.DELETED) {
            member.softDelete();
        }
        return member;
    }
}

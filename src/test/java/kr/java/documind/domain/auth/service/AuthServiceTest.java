package kr.java.documind.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import java.util.UUID;
import kr.java.documind.domain.auth.exception.InvalidTokenException;
import kr.java.documind.domain.auth.exception.TokenExpiredException;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.auth.model.enums.OAuthProvider;
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
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @InjectMocks private AuthService authService;

    @Mock private TokenProvider tokenProvider;

    @Mock private RedisTokenService redisTokenService;

    @Mock private MemberService memberService;

    @Mock private JwtProperties jwtProperties;

    @Nested
    @DisplayName("refresh() 단위 테스트")
    class RefreshTest {

        @Test
        @DisplayName("토큰 재발급: refreshToken이 null이면 UnauthorizedException 발생")
        void refresh_refreshToken이Null이면_UnauthorizedException을던진다() {
            // Given

            // When & Then
            assertThatThrownBy(() -> authService.refresh(null))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Refresh Token이 없습니다");
        }

        @Test
        @DisplayName("토큰 재발급: refreshToken이 blank면 UnauthorizedException 발생")
        void refresh_refreshToken이Blank이면_UnauthorizedException을던진다() {
            // Given
            String blankRefreshToken = " ";

            // When & Then
            assertThatThrownBy(() -> authService.refresh(blankRefreshToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Refresh Token이 없습니다");
        }

        @Test
        @DisplayName("토큰 재발급: 만료된 refreshToken이면 TokenExpiredException 발생")
        void refresh_refreshToken이만료되면_TokenExpiredException을던진다() {
            // Given
            String expiredRefreshToken = "expired.refresh.token";
            willThrow(new TokenExpiredException())
                    .given(tokenProvider)
                    .validateRefreshToken(expiredRefreshToken);

            // When & Then
            assertThatThrownBy(() -> authService.refresh(expiredRefreshToken))
                    .isInstanceOf(TokenExpiredException.class)
                    .hasMessageContaining("토큰이 만료되었습니다");
        }

        @Test
        @DisplayName("토큰 재발급: 유효하지 않은 refreshToken이면 InvalidTokenException 발생")
        void refresh_refreshToken이유효하지않으면_InvalidTokenException을던진다() {
            // Given
            String invalidRefreshToken = "invalid.refresh.token";
            willThrow(new InvalidTokenException())
                    .given(tokenProvider)
                    .validateRefreshToken(invalidRefreshToken);

            // When & Then
            assertThatThrownBy(() -> authService.refresh(invalidRefreshToken))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("유효하지 않은 토큰입니다");
        }

        @Test
        @DisplayName("토큰 재발급: SUSPENDED 계정이면 ForbiddenException 발생 및 refreshToken 삭제")
        void refresh_계정이SUSPENDED이면_ForbiddenException을던지고RefreshToken을삭제한다() {
            // Given
            String refreshToken = "valid.refresh.token";
            UUID memberId = UUID.randomUUID();
            Member suspendedMember = createMember(AccountStatus.SUSPENDED);

            given(tokenProvider.getMemberId(refreshToken)).willReturn(memberId);
            given(tokenProvider.getGlobalRole(refreshToken)).willReturn(GlobalRole.EMPLOYEE);
            given(memberService.getMemberWithCompany(memberId)).willReturn(suspendedMember);

            // When & Then
            assertThatThrownBy(() -> authService.refresh(refreshToken))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("비활성화");

            then(redisTokenService).should().deleteRefreshToken(memberId);
        }

        @Test
        @DisplayName("토큰 재발급: DELETED 계정이면 ForbiddenException 발생 및 refreshToken 삭제")
        void refresh_계정이DELETED이면_ForbiddenException을던지고RefreshToken을삭제한다() {
            // Given
            String refreshToken = "valid.refresh.token";
            UUID memberId = UUID.randomUUID();
            Member deletedMember = createMember(AccountStatus.DELETED);

            given(tokenProvider.getMemberId(refreshToken)).willReturn(memberId);
            given(tokenProvider.getGlobalRole(refreshToken)).willReturn(GlobalRole.EMPLOYEE);
            given(memberService.getMemberWithCompany(memberId)).willReturn(deletedMember);

            // When & Then
            assertThatThrownBy(() -> authService.refresh(refreshToken))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("비활성화");

            then(redisTokenService).should().deleteRefreshToken(memberId);
        }

        @Test
        @DisplayName("토큰 재발급: Redis rotate에 실패하면 UnauthorizedException 발생")
        void refresh_rotateRefreshToken에실패하면_UnauthorizedException을던진다() {
            // Given
            String oldRefreshToken = "valid.old.refresh.token";
            String newRefreshToken = "new.refresh.token";
            UUID memberId = UUID.randomUUID();
            Member activeMember = createMember(AccountStatus.ACTIVE);

            given(tokenProvider.getMemberId(oldRefreshToken)).willReturn(memberId);
            given(tokenProvider.getGlobalRole(oldRefreshToken)).willReturn(GlobalRole.EMPLOYEE);
            given(memberService.getMemberWithCompany(memberId)).willReturn(activeMember);
            given(tokenProvider.generateRefreshToken(memberId, GlobalRole.EMPLOYEE))
                    .willReturn(newRefreshToken);
            given(jwtProperties.getRefreshExpirationSeconds()).willReturn(604800L);
            given(
                            redisTokenService.rotateRefreshToken(
                                    memberId, oldRefreshToken, newRefreshToken, 604800L))
                    .willReturn(false);

            // When & Then
            assertThatThrownBy(() -> authService.refresh(oldRefreshToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("유효하지 않은 Refresh Token");
        }

        @Test
        @DisplayName("토큰 재발급: ACTIVE 계정의 유효한 토큰이면 새 토큰 쌍 반환")
        void refresh_ACTIVE계정의유효한토큰이면_새토큰쌍을반환한다() {
            // Given
            String oldRefreshToken = "valid.old.refresh.token";
            String newAccessToken = "new.access.token";
            String newRefreshToken = "new.refresh.token";
            UUID memberId = UUID.randomUUID();
            Member activeMember = createMember(AccountStatus.ACTIVE);

            given(tokenProvider.getMemberId(oldRefreshToken)).willReturn(memberId);
            given(tokenProvider.getGlobalRole(oldRefreshToken)).willReturn(GlobalRole.CEO);
            given(memberService.getMemberWithCompany(memberId)).willReturn(activeMember);
            given(tokenProvider.generateRefreshToken(memberId, GlobalRole.CEO))
                    .willReturn(newRefreshToken);
            given(jwtProperties.getRefreshExpirationSeconds()).willReturn(604800L);
            given(
                            redisTokenService.rotateRefreshToken(
                                    memberId, oldRefreshToken, newRefreshToken, 604800L))
                    .willReturn(true);
            given(tokenProvider.generateAccessToken(memberId, GlobalRole.CEO))
                    .willReturn(newAccessToken);

            // When
            AuthService.AuthTokens result = authService.refresh(oldRefreshToken);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.accessToken()).isEqualTo(newAccessToken);
            assertThat(result.refreshToken()).isEqualTo(newRefreshToken);
        }
    }

    @Nested
    @DisplayName("logout() 단위 테스트")
    class LogoutTest {

        @Test
        @DisplayName("로그아웃: 인증 상태에서 유효한 accessToken이면 블랙리스트 등록 후 refreshToken 삭제")
        void logout_인증상태에서유효한AccessToken이면_블랙리스트등록후RefreshToken을삭제한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String accessToken = "valid.access.token";
            String refreshToken = "valid.refresh.token";
            given(tokenProvider.getRemainingMillis(accessToken)).willReturn(60_000L);

            // When
            authService.logout(accessToken, refreshToken, memberId);

            // Then
            then(redisTokenService).should().addToBlacklist(accessToken, 60_000L);
            then(redisTokenService).should().deleteRefreshToken(memberId);
        }

        @Test
        @DisplayName("로그아웃: 인증 상태에서 만료된 accessToken이면 블랙리스트 등록 없이 refreshToken만 삭제")
        void logout_인증상태에서만료된AccessToken이면_블랙리스트등록없이RefreshToken만삭제한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String expiredAccessToken = "expired.access.token";
            String refreshToken = "valid.refresh.token";
            given(tokenProvider.getRemainingMillis(expiredAccessToken)).willReturn(-100L);

            // When
            authService.logout(expiredAccessToken, refreshToken, memberId);

            // Then
            then(redisTokenService).should(never()).addToBlacklist(anyString(), anyLong());
            then(redisTokenService).should().deleteRefreshToken(memberId);
        }

        @Test
        @DisplayName("로그아웃: 인증 상태에서 accessToken이 null이면 refreshToken만 삭제")
        void logout_인증상태에서AccessToken이Null이면_RefreshToken만삭제한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String refreshToken = "valid.refresh.token";

            // When
            authService.logout(null, refreshToken, memberId);

            // Then
            then(tokenProvider).should(never()).getRemainingMillis(anyString());
            then(redisTokenService).should(never()).addToBlacklist(anyString(), anyLong());
            then(redisTokenService).should().deleteRefreshToken(memberId);
        }

        @Test
        @DisplayName("로그아웃: 인증 상태에서 accessToken이 blank면 refreshToken만 삭제")
        void logout_인증상태에서AccessToken이Blank이면_RefreshToken만삭제한다() {
            // Given
            UUID memberId = UUID.randomUUID();
            String refreshToken = "valid.refresh.token";
            String blankAccessToken = " ";

            // When
            authService.logout(blankAccessToken, refreshToken, memberId);

            // Then
            then(tokenProvider).should(never()).getRemainingMillis(anyString());
            then(redisTokenService).should(never()).addToBlacklist(anyString(), anyLong());
            then(redisTokenService).should().deleteRefreshToken(memberId);
        }

        @Test
        @DisplayName("로그아웃: 미인증 상태에서 refreshToken이 있으면 memberId 추출 후 refreshToken 삭제")
        void logout_미인증상태에서RefreshToken이있으면_MemberId를추출한후RefreshToken을삭제한다() {
            // Given
            UUID extractedMemberId = UUID.randomUUID();
            String refreshToken = "expired.or.valid.refresh.token";
            given(tokenProvider.getMemberIdAllowExpired(refreshToken))
                    .willReturn(extractedMemberId);

            // When
            authService.logout(null, refreshToken, null);

            // Then
            then(redisTokenService).should().deleteRefreshToken(extractedMemberId);
        }

        @Test
        @DisplayName("로그아웃: 미인증 상태에서 모든 토큰이 null이면 아무 처리 없이 종료")
        void logout_미인증상태에서모든토큰이Null이면_아무처리없이종료한다() {
            // Given

            // When
            authService.logout(null, null, null);

            // Then
            then(tokenProvider).should(never()).getMemberIdAllowExpired(anyString());
            then(redisTokenService).should(never()).deleteRefreshToken(any(UUID.class));
        }

        @Test
        @DisplayName("로그아웃: 미인증 상태에서 refreshToken이 blank면 아무 처리 없이 종료")
        void logout_미인증상태에서RefreshToken이Blank이면_아무처리없이종료한다() {
            // Given
            String blankRefreshToken = " ";

            // When
            authService.logout(null, blankRefreshToken, null);

            // Then
            then(tokenProvider).should(never()).getMemberIdAllowExpired(anyString());
            then(redisTokenService).should(never()).deleteRefreshToken(any(UUID.class));
        }

        @Test
        @DisplayName("로그아웃: 미인증 상태에서 refreshToken 파싱에 실패하면 예외를 전파하지 않는다")
        void logout_미인증상태에서RefreshToken파싱에실패하면_예외를전파하지않는다() {
            // Given
            String corruptedToken = "corrupted.token.signature";
            given(tokenProvider.getMemberIdAllowExpired(corruptedToken))
                    .willThrow(new InvalidTokenException());

            // When
            authService.logout(null, corruptedToken, null);

            // Then
            then(redisTokenService).should(never()).deleteRefreshToken(any(UUID.class));
        }
    }

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

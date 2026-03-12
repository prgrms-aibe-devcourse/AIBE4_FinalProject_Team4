package kr.java.documind.domain.auth.service;

import java.util.UUID;
import kr.java.documind.domain.auth.exception.InvalidTokenException;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.service.MemberService;
import kr.java.documind.global.config.JwtProperties;
import kr.java.documind.global.exception.ForbiddenException;
import kr.java.documind.global.exception.UnauthorizedException;
import kr.java.documind.global.security.RedisTokenService;
import kr.java.documind.global.security.jwt.TokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TokenProvider tokenProvider;
    private final RedisTokenService redisTokenService;
    private final MemberService memberService;
    private final JwtProperties jwtProperties;

    public AuthTokens refresh(String refreshToken) {
        validateRefreshTokenPresence(refreshToken);
        tokenProvider.validateRefreshToken(refreshToken);

        UUID memberId = tokenProvider.getMemberId(refreshToken);
        GlobalRole globalRole = tokenProvider.getGlobalRole(refreshToken);

        validateActiveMember(memberId);

        String newRefreshToken = tokenProvider.generateRefreshToken(memberId, globalRole);
        rotateRefreshToken(memberId, refreshToken, newRefreshToken);

        String newAccessToken = tokenProvider.generateAccessToken(memberId, globalRole);

        log.debug("[AuthService] 토큰 재발급 완료: memberId={}", memberId);
        return new AuthTokens(newAccessToken, newRefreshToken);
    }

    public void logout(String accessToken, String refreshToken, UUID memberId) {
        if (memberId != null) {
            logoutAuthenticatedMember(accessToken, memberId);
            return;
        }

        logoutWithRefreshToken(refreshToken);
    }

    private void validateRefreshTokenPresence(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh Token이 없습니다.");
        }
    }

    private void validateActiveMember(UUID memberId) {
        Member member = memberService.getMemberWithCompany(memberId);

        if (!member.isActive()) {
            redisTokenService.deleteRefreshToken(memberId);

            log.warn(
                "[AuthService] 비활성 계정의 토큰 갱신 시도: memberId={} status={}",
                memberId,
                member.getAccountStatus());

            throw new ForbiddenException("계정이 비활성화되었습니다. 다시 로그인하세요.");
        }
    }

    private void rotateRefreshToken(UUID memberId, String oldRefreshToken, String newRefreshToken) {
        boolean rotated = redisTokenService.rotateRefreshToken(
            memberId,
            oldRefreshToken,
            newRefreshToken,
            jwtProperties.getRefreshExpirationSeconds());

        if (!rotated) {
            log.warn("[AuthService] Refresh Token 교체 실패: memberId={}", memberId);
            throw new UnauthorizedException("유효하지 않은 Refresh Token입니다. 다시 로그인하세요.");
        }
    }

    private void logoutAuthenticatedMember(String accessToken, UUID memberId) {
        if (accessToken != null && !accessToken.isBlank()) {
            long remainingMillis = tokenProvider.getRemainingMillis(accessToken);
            if (remainingMillis > 0) {
                redisTokenService.addToBlacklist(accessToken, remainingMillis);
            }
        }

        redisTokenService.deleteRefreshToken(memberId);
        log.info("[AuthService] 로그아웃: memberId={}", memberId);
    }

    private void logoutWithRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            UUID extractedMemberId = tokenProvider.getMemberIdAllowExpired(refreshToken);
            redisTokenService.deleteRefreshToken(extractedMemberId);
            log.info("[AuthService] Refresh Token 기반 로그아웃 처리: memberId={}", extractedMemberId);
        } catch (InvalidTokenException e) {
            log.debug("[AuthService] Refresh Token에서 memberId 추출 실패: {}", e.getMessage());
        }
    }

    public record AuthTokens(String accessToken, String refreshToken) {}
}

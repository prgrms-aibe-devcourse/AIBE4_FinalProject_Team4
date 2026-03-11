package kr.java.documind.domain.auth.service;

import io.jsonwebtoken.ExpiredJwtException;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TokenProvider jwtProvider;
    private final RedisTokenService redisTokenService;
    private final MemberService memberService;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null) {
            throw new UnauthorizedException("Refresh Token이 없습니다.");
        }

        try {
            jwtProvider.validateToken(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("Refresh Token이 만료되었습니다. 다시 로그인하세요.", e);
        } catch (UnauthorizedException e) {
            throw new UnauthorizedException("유효하지 않은 Refresh Token입니다. 다시 로그인하세요.", e);
        }

        UUID memberId = jwtProvider.getMemberId(refreshToken);
        GlobalRole globalRole = jwtProvider.getGlobalRole(refreshToken);

        String storedToken = redisTokenService.consumeRefreshToken(memberId);
        if (!refreshToken.equals(storedToken)) {
            log.warn("Refresh Token 불일치 — 탈취 가능성: memberId={}", memberId);
            throw new UnauthorizedException("유효하지 않은 Refresh Token입니다. 다시 로그인하세요.");
        }

        Member member = memberService.getMemberWithCompany(memberId);
        if (!member.isActive()) {
            log.warn(
                    "[AuthService] 비활성 계정의 토큰 갱신 시도: memberId={} status={}",
                    memberId,
                    member.getAccountStatus());
            throw new ForbiddenException("계정이 비활성화되었습니다. 다시 로그인하세요.");
        }

        String newAccessToken = jwtProvider.generateAccessToken(memberId, globalRole);
        String newRefreshToken = jwtProvider.generateRefreshToken(memberId, globalRole);

        redisTokenService.saveRefreshToken(
                memberId, newRefreshToken, jwtProperties.getRefreshExpirationSeconds());

        log.debug("[AuthService] Access Token 재발급 완료: memberId={}", memberId);

        return new AuthTokens(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String accessToken, String refreshToken, UUID memberId) {
        if (memberId != null) {
            // 인증된 상태에서의 로그아웃
            if (accessToken != null) {
                long remainingMillis = jwtProvider.getRemainingMillis(accessToken);
                if (remainingMillis > 0) {
                    redisTokenService.addToBlacklist(accessToken, remainingMillis);
                }
            }
            redisTokenService.deleteRefreshToken(memberId);
            log.info("[AuthService] 로그아웃: memberId={}", memberId);
        } else if (refreshToken != null) {
            // 만료된 토큰 등으로 인증 정보가 없을 때 Refresh Token을 이용한 로그아웃 시도
            try {
                UUID extractedMemberId = jwtProvider.getMemberIdFromExpiredToken(refreshToken);
                redisTokenService.deleteRefreshToken(extractedMemberId);
                log.info(
                        "[AuthService] 만료된 토큰으로 로그아웃 처리 (Refresh Token 정리): memberId={}",
                        extractedMemberId);
            } catch (Exception e) {
                log.debug("Refresh Token에서 memberId 추출 실패 (이미 정리되었거나 서명 오류): {}", e.getMessage());
            }
        }
    }

    public record AuthTokens(String accessToken, String refreshToken) {}
}

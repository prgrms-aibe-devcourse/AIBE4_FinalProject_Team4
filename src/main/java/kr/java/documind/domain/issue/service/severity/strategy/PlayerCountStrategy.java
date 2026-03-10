package kr.java.documind.domain.issue.service.severity.strategy;

import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.issue.service.tracking.UserCountTracker;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.global.config.SeverityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 영향받은 플레이어 수 기반 심각도 점수 계산 (0-20점)
 *
 * <p>Redis HyperLogLog로 unique userId 카운팅
 *
 * <p>점수 기준: application.yml의 issue.severity.player-count.thresholds 설정 참조
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerCountStrategy implements SeverityStrategy {

    private final UserCountTracker userCountTracker;
    private final SeverityProperties severityProperties;

    // ThreadLocal 캐시: calculate()와 generateReason() 간 중복 Redis 조회 방지
    private final ThreadLocal<Long> cachedUserCount = new ThreadLocal<>();

    @Override
    public int calculate(Issue issue, GameLog log) {
        // Redis HyperLogLog에서 실제 플레이어 수 조회
        long userCount = userCountTracker.getAffectedUserCount(issue.getFingerprint());

        // ThreadLocal에 캐시 (같은 요청 스레드 내 generateReason()에서 재사용)
        cachedUserCount.set(userCount);

        return mapUserCountToScore(userCount);
    }

    /**
     * 플레이어 수를 점수로 매핑 (설정값 기반)
     *
     * @param userCount 영향받은 플레이어 수
     * @return 점수 (0-20)
     */
    private int mapUserCountToScore(long userCount) {
        return severityProperties.getPlayerCount().getThresholds().stream()
                .filter(threshold -> userCount >= threshold.getCount())
                .mapToInt(SeverityProperties.Threshold::getScore)
                .max()
                .orElse(0);
    }

    @Override
    public SeverityFactor getFactor() {
        return SeverityFactor.PLAYER_COUNT;
    }

    @Override
    public String generateReason(int score, Issue issue, GameLog gameLog) {
        if (score == 0) {
            cachedUserCount.remove(); // 메모리 누수 방지
            return null;
        }

        // ThreadLocal 캐시에서 조회 (중복 Redis 호출 방지)
        Long userCount = cachedUserCount.get();

        // Fallback: ThreadLocal 값이 없으면 재조회 (방어적 프로그래밍)
        if (userCount == null) {
            log.warn(
                    "ThreadLocal 캐시 없음. Redis 재조회. issueId={}, fingerprint={}",
                    issue.getId(),
                    issue.getFingerprint());
            userCount = userCountTracker.getAffectedUserCount(issue.getFingerprint());
        }

        // ThreadLocal 정리 (메모리 누수 방지)
        cachedUserCount.remove();

        return String.format("플레이어 %,d명 영향 (%d점)", userCount, score);
    }
}

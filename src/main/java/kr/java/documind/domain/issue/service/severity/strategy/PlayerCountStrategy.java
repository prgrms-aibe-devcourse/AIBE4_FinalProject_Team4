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

    @Override
    public int calculate(Issue issue, GameLog log) {
        // Redis HyperLogLog에서 실제 플레이어 수 조회
        long userCount = userCountTracker.getAffectedUserCount(issue.getFingerprint());

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
    public String generateReason(int score, Issue issue, GameLog log) {
        if (score == 0) {
            return null;
        }

        // Redis HyperLogLog에서 실제 플레이어 수 조회
        long userCount = userCountTracker.getAffectedUserCount(issue.getFingerprint());

        return String.format("플레이어 %,d명 영향 (%d점)", userCount, score);
    }
}

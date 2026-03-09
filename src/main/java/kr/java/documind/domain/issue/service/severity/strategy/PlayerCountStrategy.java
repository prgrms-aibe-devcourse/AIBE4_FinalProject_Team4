package kr.java.documind.domain.issue.service.severity.strategy;

import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.issue.service.tracking.UserCountTracker;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 영향받은 플레이어 수 기반 심각도 점수 계산 (0-20점)
 *
 * <p>Redis HyperLogLog로 unique userId 카운팅
 *
 * <p>점수 기준 (Developer Guide 기준):
 *
 * <ul>
 *   <li>1,000명 이상: 20점
 *   <li>500-999명: 18점
 *   <li>100-499명: 15점
 *   <li>50-99명: 12점
 *   <li>10-49명: 8점
 *   <li>5-9명: 5점
 *   <li>1-4명: 2점
 *   <li>0명: 0점
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerCountStrategy implements SeverityStrategy {

    private final UserCountTracker userCountTracker;

    @Override
    public int calculate(Issue issue, GameLog log) {
        // Redis HyperLogLog에서 실제 플레이어 수 조회
        long userCount = userCountTracker.getAffectedUserCount(issue.getFingerprint());

        return mapUserCountToScore(userCount);
    }

    /**
     * 플레이어 수를 점수로 매핑
     *
     * @param userCount 영향받은 플레이어 수
     * @return 점수 (0-20)
     */
    private int mapUserCountToScore(long userCount) {
        if (userCount >= 1000) return 20;
        if (userCount >= 500) return 18;
        if (userCount >= 100) return 15;
        if (userCount >= 50) return 12;
        if (userCount >= 10) return 8;
        if (userCount >= 5) return 5;
        if (userCount >= 1) return 2;
        return 0;
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

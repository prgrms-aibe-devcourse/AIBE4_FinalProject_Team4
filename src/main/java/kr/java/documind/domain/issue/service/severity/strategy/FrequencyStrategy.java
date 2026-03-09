package kr.java.documind.domain.issue.service.severity.strategy;

import java.time.Duration;
import java.time.OffsetDateTime;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 발생 빈도 기반 심각도 점수 계산 (0-20점)
 *
 * <p>시간당 발생 횟수로 점수 계산
 *
 * <p>점수 기준 (Developer Guide 기준):
 *
 * <ul>
 *   <li>1,000건 이상/시: 20점 (대량 발생!)
 *   <li>500-999건/시: 18점
 *   <li>100-499건/시: 15점
 *   <li>50-99건/시: 12점
 *   <li>10-49건/시: 8점
 *   <li>5-9건/시: 5점
 *   <li>1-4건/시: 2점
 *   <li>0건/시: 0점
 * </ul>
 */
@Slf4j
@Component
public class FrequencyStrategy implements SeverityStrategy {

    /** 빈도 계산 기준 시간 (1시간) */
    private static final long FREQUENCY_WINDOW_HOURS = 1;

    @Override
    public int calculate(Issue issue, GameLog log) {
        // 시간당 발생 횟수 계산
        long occurrencesPerHour = calculateOccurrencesPerHour(issue);

        return mapFrequencyToScore(occurrencesPerHour);
    }

    /**
     * 시간당 발생 횟수 계산
     *
     * <p>첫 발생 시각부터 마지막 발생 시각까지의 시간을 기준으로 시간당 평균 발생 횟수 계산
     *
     * @param issue 이슈
     * @return 시간당 발생 횟수
     */
    private long calculateOccurrencesPerHour(Issue issue) {
        OffsetDateTime firstOccurred = issue.getFirstOccurredAt();
        OffsetDateTime lastOccurred = issue.getLastOccurredAt();
        int occurrenceCount = issue.getOccurrenceCount();

        // 단일 발생 시
        if (occurrenceCount == 1) {
            return 1;
        }

        // 경과 시간 계산 (분 단위)
        long elapsedMinutes = Duration.between(firstOccurred, lastOccurred).toMinutes();

        // 경과 시간이 0이면 (거의 동시 발생) → 높은 빈도로 간주
        if (elapsedMinutes == 0) {
            return occurrenceCount; // 분당 N건 = 시간당 N건으로 근사
        }

        // 시간당 발생 횟수 = 총 발생 횟수 / (경과 시간 / 60)
        double elapsedHours = elapsedMinutes / 60.0;
        return Math.round(occurrenceCount / elapsedHours);
    }

    /**
     * 빈도를 점수로 매핑
     *
     * @param occurrencesPerHour 시간당 발생 횟수
     * @return 점수 (0-20)
     */
    private int mapFrequencyToScore(long occurrencesPerHour) {
        if (occurrencesPerHour >= 1000) return 20;
        if (occurrencesPerHour >= 500) return 18;
        if (occurrencesPerHour >= 100) return 15;
        if (occurrencesPerHour >= 50) return 12;
        if (occurrencesPerHour >= 10) return 8;
        if (occurrencesPerHour >= 5) return 5;
        if (occurrencesPerHour >= 1) return 2;
        return 0;
    }

    @Override
    public SeverityFactor getFactor() {
        return SeverityFactor.FREQUENCY;
    }

    @Override
    public String generateReason(int score, Issue issue, GameLog log) {
        if (score == 0) {
            return null;
        }

        long occurrencesPerHour = calculateOccurrencesPerHour(issue);

        return String.format("시간당 %,d회 발생 (%d점)", occurrencesPerHour, score);
    }
}

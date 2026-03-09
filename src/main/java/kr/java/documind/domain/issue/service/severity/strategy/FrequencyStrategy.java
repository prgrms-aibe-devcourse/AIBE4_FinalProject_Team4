package kr.java.documind.domain.issue.service.severity.strategy;

import java.time.Duration;
import java.time.OffsetDateTime;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.global.config.SeverityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 발생 빈도 기반 심각도 점수 계산 (0-20점)
 *
 * <p>시간당 발생 횟수로 점수 계산
 *
 * <p>점수 기준: application.yml의 issue.severity.frequency.thresholds 설정 참조
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FrequencyStrategy implements SeverityStrategy {

    private final SeverityProperties severityProperties;

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

        // 경과 시간이 0이면 (1분 내 버스트) → 시간당으로 환산
        // 예: 10초에 1000번 발생 = 시간당 60,000번으로 추정
        if (elapsedMinutes == 0) {
            return occurrenceCount * 60; // 분당 N건 → 시간당 N*60건
        }

        // 시간당 발생 횟수 = 총 발생 횟수 / (경과 시간 / 60)
        double elapsedHours = elapsedMinutes / 60.0;
        return Math.round(occurrenceCount / elapsedHours);
    }

    /**
     * 빈도를 점수로 매핑 (설정값 기반)
     *
     * @param occurrencesPerHour 시간당 발생 횟수
     * @return 점수 (0-20)
     */
    private int mapFrequencyToScore(long occurrencesPerHour) {
        return severityProperties.getFrequency().getThresholds().stream()
                .filter(threshold -> occurrencesPerHour >= threshold.getCount())
                .mapToInt(SeverityProperties.Threshold::getScore)
                .max()
                .orElse(0);
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

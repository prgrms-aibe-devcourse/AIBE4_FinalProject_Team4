package kr.java.documind.domain.issue.service.severity.strategy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.issue.service.tracking.UserCountTracker;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.global.config.SeverityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 발생 빈도 기반 심각도 점수 계산 (0-20점)
 *
 * <p>전체 로그 대비 에러 발생 비율로 점수 계산 (동시 접속자 수 영향 제거)
 *
 * <p>점수 기준: application.yml의 issue.severity.frequency.thresholds 설정 참조
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FrequencyStrategy implements SeverityStrategy {

    private final SeverityProperties severityProperties;
    private final UserCountTracker userCountTracker;

    // Redis 중복 조회 방지를 위한 캐시 (calculate()와 generateReason() 간 공유)
    private double cachedErrorRate = 0.0;

    @Override
    public int calculate(Issue issue, GameLog log) {
        // 에러 발생 비율 계산 (전체 로그 대비)
        cachedErrorRate = calculateErrorRate(issue);

        return mapErrorRateToScore(cachedErrorRate);
    }

    /**
     * 에러 발생 비율 계산 (전체 로그 대비)
     *
     * <p>동시 접속자 수 영향을 제거하기 위해 비율 사용
     *
     * <p>Redis TTL(7일) 제약으로 인해 계산 범위를 최근 7일로 제한
     *
     * @param issue 이슈
     * @return 에러 발생 비율 (%, 0.0 ~ 100.0)
     */
    private double calculateErrorRate(Issue issue) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime firstOccurred = issue.getFirstOccurredAt();
        OffsetDateTime lastOccurred = issue.getLastOccurredAt();
        long errorCount = issue.getOccurrenceCount();

        // Redis TTL(7일) 이내 데이터만 사용
        // 8일 이상 지속된 이슈는 최근 7일 데이터만으로 에러율 계산
        OffsetDateTime limitedStart = firstOccurred;
        if (Duration.between(firstOccurred, now).toDays() > 7) {
            limitedStart = now.minusDays(7);
            log.debug(
                    "이슈 발생 기간이 7일 초과. 최근 7일 데이터만 사용. issueId={}, originalStart={}, limitedStart={}",
                    issue.getId(),
                    firstOccurred,
                    limitedStart);
        }

        // Redis에서 전체 로그 수 조회
        long totalLogs =
                userCountTracker.getTotalLogsInTimeRange(
                        issue.getProjectId(), limitedStart, lastOccurred);

        // 전체 로그가 없으면 fallback: 절대적인 시간당 발생 횟수 사용
        if (totalLogs == 0) {
            log.warn(
                    "전체 로그 데이터 없음. 절대적인 발생 횟수로 fallback. issueId={}, fingerprint={}",
                    issue.getId(),
                    issue.getFingerprint());
            return calculateFallbackRate(errorCount, limitedStart, lastOccurred);
        }

        // 에러율 = (에러 발생 횟수 / 전체 로그 수) * 100
        double errorRate = ((double) errorCount / totalLogs) * 100;

        log.debug(
                "에러율 계산 완료: errorCount={}, totalLogs={}, errorRate={}%, issueId={}",
                errorCount,
                totalLogs,
                String.format("%.2f", errorRate),
                issue.getId());

        return errorRate;
    }

    /**
     * Fallback: 전체 로그 데이터 없을 때 절대적인 시간당 발생 횟수를 비율처럼 사용
     *
     * <p>시간당 100회 이상 = 1% 비율로 간주 (경험적 매핑)
     *
     * @param errorCount 에러 발생 횟수
     * @param firstOccurred 첫 발생 시각
     * @param lastOccurred 마지막 발생 시각
     * @return 가상 에러율 (%)
     */
    private double calculateFallbackRate(
            long errorCount, OffsetDateTime firstOccurred, OffsetDateTime lastOccurred) {
        // 단일 발생 시
        if (errorCount == 1) {
            return 0.01; // 0.01%
        }

        // 경과 시간 계산 (분 단위)
        long elapsedMinutes = Duration.between(firstOccurred, lastOccurred).toMinutes();

        // 경과 시간이 0이면 (1분 내 버스트) → 시간당으로 환산
        long occurrencesPerHour;
        if (elapsedMinutes == 0) {
            occurrencesPerHour = errorCount * 60; // 분당 N건 → 시간당 N*60건
        } else {
            double elapsedHours = elapsedMinutes / 60.0;
            occurrencesPerHour = Math.round(errorCount / elapsedHours);
        }

        // 시간당 100회 = 1% 비율로 간주 (경험적 매핑)
        return occurrencesPerHour / 100.0;
    }

    /**
     * 에러율을 점수로 매핑 (설정값 기반)
     *
     * @param errorRate 에러 발생 비율 (%)
     * @return 점수 (0-20)
     */
    private int mapErrorRateToScore(double errorRate) {
        return severityProperties.getFrequency().getThresholds().stream()
                .filter(threshold -> errorRate >= threshold.getRate())
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

        // 캐시된 에러율 사용 (중복 계산 방지)
        return String.format("에러율 %.2f%% (%d점)", cachedErrorRate, score);
    }
}

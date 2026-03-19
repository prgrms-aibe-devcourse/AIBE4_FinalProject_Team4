package kr.java.documind.domain.issue.service.severity.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.issue.service.tracking.UserCountTracker;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import kr.java.documind.global.config.SeverityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@DisplayName("FrequencyStrategy 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FrequencyStrategyTest {

    @Mock private SeverityProperties severityProperties;

    @Mock private UserCountTracker userCountTracker;

    @Mock private kr.java.documind.domain.logprocessor.model.repository.LogJdbcRepository logJdbcRepository;

    @InjectMocks private FrequencyStrategy strategy;

    @BeforeEach
    void setUp() {
        SeverityProperties.FrequencyConfig frequencyConfig =
                new SeverityProperties.FrequencyConfig();
        frequencyConfig.setThresholds(
                List.of(
                        createThreshold(10.0, 20), // 10% 이상
                        createThreshold(5.0, 18), // 5% 이상
                        createThreshold(2.0, 15), // 2% 이상
                        createThreshold(1.0, 12), // 1% 이상
                        createThreshold(0.5, 8), // 0.5% 이상
                        createThreshold(0.1, 5), // 0.1% 이상
                        createThreshold(0.01, 2))); // 0.01% 이상

        given(severityProperties.getFrequency()).willReturn(frequencyConfig);

        // LogJdbcRepository Mock 기본 동작 설정 (occurrence count 반환)
        // 각 테스트에서 issue의 occurrenceCount를 반환하도록 설정
        given(logJdbcRepository.countByProjectIdAndFingerprintAndOccurredAtBetween(
                        any(), any(), any(), any()))
                .willAnswer(invocation -> {
                    // 기본값 반환 (각 테스트에서 필요시 재정의)
                    return 0L;
                });
    }

    private SeverityProperties.Threshold createThreshold(double rate, int score) {
        SeverityProperties.Threshold threshold = new SeverityProperties.Threshold();
        threshold.setRate(rate);
        threshold.setScore(score);
        return threshold;
    }

    @Test
    @DisplayName("에러율 10% 이상 시 20점을 반환한다")
    void calculate10PercentPlus() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 1000); // 에러 1000건
        GameLog log = createGameLog();

        // 전체 로그 10,000건 → 에러율 10%
        given(logJdbcRepository.countByProjectIdAndFingerprintAndOccurredAtBetween(any(), any(), any(), any())).willReturn(1000L);
        given(userCountTracker.getTotalLogsInTimeRange(any(), any(), any())).willReturn(10000L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("에러율 5~9.99% 시 18점을 반환한다")
    void calculate5To9Percent() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 700); // 에러 700건
        GameLog log = createGameLog();

        // 전체 로그 10,000건 → 에러율 7%
        given(logJdbcRepository.countByProjectIdAndFingerprintAndOccurredAtBetween(any(), any(), any(), any())).willReturn(700L);
        given(userCountTracker.getTotalLogsInTimeRange(any(), any(), any())).willReturn(10000L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(18);
    }

    @Test
    @DisplayName("에러율 2~4.99% 시 15점을 반환한다")
    void calculate2To4Percent() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 300); // 에러 300건
        GameLog log = createGameLog();

        // 전체 로그 10,000건 → 에러율 3%
        given(logJdbcRepository.countByProjectIdAndFingerprintAndOccurredAtBetween(any(), any(), any(), any())).willReturn(300L);
        given(userCountTracker.getTotalLogsInTimeRange(any(), any(), any())).willReturn(10000L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(15);
    }

    @Test
    @DisplayName("에러율 1~1.99% 시 12점을 반환한다")
    void calculate1To1Percent() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 150); // 에러 150건
        GameLog log = createGameLog();

        // 전체 로그 10,000건 → 에러율 1.5%
        given(logJdbcRepository.countByProjectIdAndFingerprintAndOccurredAtBetween(any(), any(), any(), any())).willReturn(150L);
        given(userCountTracker.getTotalLogsInTimeRange(any(), any(), any())).willReturn(10000L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(12);
    }

    @Test
    @DisplayName("에러율 0.5~0.99% 시 8점을 반환한다")
    void calculate0Point5To0Point9Percent() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 70); // 에러 70건
        GameLog log = createGameLog();

        // 전체 로그 10,000건 → 에러율 0.7%
        given(logJdbcRepository.countByProjectIdAndFingerprintAndOccurredAtBetween(any(), any(), any(), any())).willReturn(70L);
        given(userCountTracker.getTotalLogsInTimeRange(any(), any(), any())).willReturn(10000L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(8);
    }

    @Test
    @DisplayName("에러율 0.1~0.49% 시 5점을 반환한다")
    void calculate0Point1To0Point4Percent() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 20); // 에러 20건
        GameLog log = createGameLog();

        // 전체 로그 10,000건 → 에러율 0.2%
        given(logJdbcRepository.countByProjectIdAndFingerprintAndOccurredAtBetween(any(), any(), any(), any())).willReturn(20L);
        given(userCountTracker.getTotalLogsInTimeRange(any(), any(), any())).willReturn(10000L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(5);
    }

    @Test
    @DisplayName("에러율 0.01~0.09% 시 2점을 반환한다")
    void calculate0Point01To0Point09Percent() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 3); // 에러 3건
        GameLog log = createGameLog();

        // 전체 로그 10,000건 → 에러율 0.03%
        given(logJdbcRepository.countByProjectIdAndFingerprintAndOccurredAtBetween(any(), any(), any(), any())).willReturn(3L);
        given(userCountTracker.getTotalLogsInTimeRange(any(), any(), any())).willReturn(10000L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(2);
    }

    @Test
    @DisplayName("전체 로그 데이터가 없을 때 fallback으로 절대 발생 횟수를 사용한다")
    void calculateFallbackWhenNoTotalLogs() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 100); // 에러 100건
        GameLog log = createGameLog();

        // 전체 로그 데이터 없음 (Redis 장애 등)
        given(logJdbcRepository.countByProjectIdAndFingerprintAndOccurredAtBetween(any(), any(), any(), any())).willReturn(100L);
        given(userCountTracker.getTotalLogsInTimeRange(any(), any(), any())).willReturn(0L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        // fallback: 시간당 100회 = 1% 비율로 간주 → 12점
        assertThat(score).isEqualTo(12);
    }

    @Test
    @DisplayName("전체 로그 데이터 없고 버스트 발생 시 fallback으로 시간당 환산한다")
    void calculateFallbackBurstScenario() {
        // given
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(occurredAt, occurredAt, 100); // 1분 내 100건 버스트
        GameLog log = createGameLog();

        // 전체 로그 데이터 없음
        given(logJdbcRepository.countByProjectIdAndFingerprintAndOccurredAtBetween(any(), any(), any(), any())).willReturn(100L);
        given(userCountTracker.getTotalLogsInTimeRange(any(), any(), any())).willReturn(0L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        // fallback: 100건 * 60 = 6000건/시 = 60% 비율로 간주 → 20점
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("7일 이상 지속된 이슈는 최근 7일 데이터만 사용한다")
    void calculateWithSevenDayLimit() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10); // 10일 전
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 500); // 에러 500건
        GameLog log = createGameLog();

        // 전체 로그 5,000건 → 에러율 10%
        given(logJdbcRepository.countByProjectIdAndFingerprintAndOccurredAtBetween(any(), any(), any(), any())).willReturn(500L);
        given(userCountTracker.getTotalLogsInTimeRange(any(), any(), any())).willReturn(5000L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(20); // 10% → 20점
        // 참고: getTotalLogsInTimeRange()는 최근 7일 범위로 호출됨
    }

    @Test
    @DisplayName("getFactor()는 FREQUENCY를 반환한다")
    void getFactor() {
        // when
        SeverityFactor factor = strategy.getFactor();

        // then
        assertThat(factor).isEqualTo(SeverityFactor.FREQUENCY);
    }

    @Test
    @DisplayName("generateReason()은 점수가 0이면 null을 반환한다")
    void generateReasonWithZeroScore() {
        // given
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(occurredAt, occurredAt, 1);
        GameLog log = createGameLog();

        // when
        String reason = strategy.generateReason(0, issue, log);

        // then
        assertThat(reason).isNull();
    }

    @Test
    @DisplayName("generateReason()은 에러율과 점수를 포함한 텍스트를 반환한다")
    void generateReasonWithScore() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 300); // 에러 300건
        GameLog log = createGameLog();

        // 전체 로그 10,000건 → 에러율 3%
        given(logJdbcRepository.countByProjectIdAndFingerprintAndOccurredAtBetween(any(), any(), any(), any())).willReturn(300L);
        given(userCountTracker.getTotalLogsInTimeRange(any(), any(), any())).willReturn(10000L);

        // calculate() 먼저 호출하여 cachedErrorRate 설정
        strategy.calculate(issue, log);

        // when
        String reason = strategy.generateReason(15, issue, log);

        // then
        assertThat(reason).contains("에러율");
        assertThat(reason).contains("3.00%");
        assertThat(reason).contains("15점");
    }

    // 테스트 헬퍼 메서드
    private Issue createIssue(
            OffsetDateTime firstOccurred, OffsetDateTime lastOccurred, int occurrenceCount) {
        UUID testProjectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return Issue.builder()
                .projectId(testProjectId)
                .fingerprint("test-fingerprint")
                .title("Test Issue")
                .errorType(ErrorType.UNKNOWN)
                .status(IssueStatus.TODO)
                .occurrenceCount(occurrenceCount)
                .firstOccurredAt(firstOccurred)
                .lastOccurredAt(lastOccurred)
                .build();
    }

    private GameLog createGameLog() {
        UUID testProjectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return GameLog.builder()
                .projectId(testProjectId)
                .fingerprint("test-fingerprint")
                .archive("Test archive")
                .severity(LogSeverity.ERROR)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}

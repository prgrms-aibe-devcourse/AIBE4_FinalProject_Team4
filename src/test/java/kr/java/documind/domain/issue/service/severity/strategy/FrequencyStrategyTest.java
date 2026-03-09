package kr.java.documind.domain.issue.service.severity.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
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

    @InjectMocks private FrequencyStrategy strategy;

    @BeforeEach
    void setUp() {
        SeverityProperties.FrequencyConfig frequencyConfig =
                new SeverityProperties.FrequencyConfig();
        frequencyConfig.setThresholds(
                List.of(
                        createThreshold(1000, 20),
                        createThreshold(500, 18),
                        createThreshold(100, 15),
                        createThreshold(50, 12),
                        createThreshold(10, 8),
                        createThreshold(5, 5),
                        createThreshold(1, 2)));

        given(severityProperties.getFrequency()).willReturn(frequencyConfig);
    }

    private SeverityProperties.Threshold createThreshold(long count, int score) {
        SeverityProperties.Threshold threshold = new SeverityProperties.Threshold();
        threshold.setCount(count);
        threshold.setScore(score);
        return threshold;
    }

    @Test
    @DisplayName("시간당 1000건 이상 발생 시 20점을 반환한다")
    void calculate1000PlusPerHour() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 1000);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("시간당 500-999건 발생 시 18점을 반환한다")
    void calculate500To999PerHour() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 600);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(18);
    }

    @Test
    @DisplayName("시간당 100-499건 발생 시 15점을 반환한다")
    void calculate100To499PerHour() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 200);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(15);
    }

    @Test
    @DisplayName("시간당 50-99건 발생 시 12점을 반환한다")
    void calculate50To99PerHour() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 70);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(12);
    }

    @Test
    @DisplayName("시간당 10-49건 발생 시 8점을 반환한다")
    void calculate10To49PerHour() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 30);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(8);
    }

    @Test
    @DisplayName("시간당 5-9건 발생 시 5점을 반환한다")
    void calculate5To9PerHour() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 7);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(5);
    }

    @Test
    @DisplayName("시간당 1-4건 발생 시 2점을 반환한다")
    void calculate1To4PerHour() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 3);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(2);
    }

    @Test
    @DisplayName("단일 발생 시 1을 반환한다")
    void calculateSingleOccurrence() {
        // given
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(occurredAt, occurredAt, 1);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(2); // 1건 → 2점
    }

    @Test
    @DisplayName("경과 시간이 0일 때 (동시 발생) occurrenceCount를 시간당 발생 횟수로 간주한다")
    void calculateZeroElapsedTime() {
        // given
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(occurredAt, occurredAt, 100);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(15); // 100건/시 → 15점
    }

    @Test
    @DisplayName("2시간에 걸쳐 200건 발생 시 시간당 100건으로 계산한다")
    void calculateTwoHoursSpan() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 200);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(15); // 100건/시 → 15점
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
    @DisplayName("generateReason()은 시간당 발생 횟수와 점수를 포함한 텍스트를 반환한다")
    void generateReasonWithScore() {
        // given
        OffsetDateTime firstOccurred = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime lastOccurred = OffsetDateTime.now(ZoneOffset.UTC);
        Issue issue = createIssue(firstOccurred, lastOccurred, 100);
        GameLog log = createGameLog();

        // when
        String reason = strategy.generateReason(15, issue, log);

        // then
        assertThat(reason).contains("시간당");
        assertThat(reason).contains("100");
        assertThat(reason).contains("15점");
    }

    // 테스트 헬퍼 메서드
    private Issue createIssue(
            OffsetDateTime firstOccurred, OffsetDateTime lastOccurred, int occurrenceCount) {
        return Issue.builder()
                .projectId(UUID.randomUUID())
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
        return GameLog.builder()
                .projectId(UUID.randomUUID())
                .fingerprint("test-fingerprint")
                .archive("Test archive")
                .severity(LogSeverity.ERROR)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}

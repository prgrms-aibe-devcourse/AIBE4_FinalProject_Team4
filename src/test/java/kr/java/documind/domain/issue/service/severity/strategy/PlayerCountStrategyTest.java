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

@DisplayName("PlayerCountStrategy 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerCountStrategyTest {

    @Mock private UserCountTracker userCountTracker;
    @Mock private SeverityProperties severityProperties;

    @InjectMocks private PlayerCountStrategy strategy;

    @BeforeEach
    void setUp() {
        SeverityProperties.PlayerCountConfig playerCountConfig =
                new SeverityProperties.PlayerCountConfig();
        playerCountConfig.setThresholds(
                List.of(
                        createThreshold(1000, 20),
                        createThreshold(500, 18),
                        createThreshold(100, 15),
                        createThreshold(50, 12),
                        createThreshold(10, 8),
                        createThreshold(5, 5),
                        createThreshold(1, 2)));

        given(severityProperties.getPlayerCount()).willReturn(playerCountConfig);
    }

    private SeverityProperties.Threshold createThreshold(long count, int score) {
        SeverityProperties.Threshold threshold = new SeverityProperties.Threshold();
        threshold.setCount(count);
        threshold.setScore(score);
        return threshold;
    }

    @Test
    @DisplayName("1000명 이상 영향 시 20점을 반환한다")
    void calculate1000PlusPlayers() {
        // given
        Issue issue = createIssue(1000);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(1000L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("500-999명 영향 시 18점을 반환한다")
    void calculate500To999Players() {
        // given
        Issue issue = createIssue(600);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(600L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(18);
    }

    @Test
    @DisplayName("100-499명 영향 시 15점을 반환한다")
    void calculate100To499Players() {
        // given
        Issue issue = createIssue(200);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(200L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(15);
    }

    @Test
    @DisplayName("50-99명 영향 시 12점을 반환한다")
    void calculate50To99Players() {
        // given
        Issue issue = createIssue(70);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(70L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(12);
    }

    @Test
    @DisplayName("10-49명 영향 시 8점을 반환한다")
    void calculate10To49Players() {
        // given
        Issue issue = createIssue(30);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(30L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(8);
    }

    @Test
    @DisplayName("5-9명 영향 시 5점을 반환한다")
    void calculate5To9Players() {
        // given
        Issue issue = createIssue(7);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(7L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(5);
    }

    @Test
    @DisplayName("1-4명 영향 시 2점을 반환한다")
    void calculate1To4Players() {
        // given
        Issue issue = createIssue(3);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(3L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(2);
    }

    @Test
    @DisplayName("0명 영향 시 0점을 반환한다")
    void calculate0Players() {
        // given
        Issue issue = createIssue(0);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(0L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("경계값 1000명 정확히는 20점을 반환한다")
    void calculateExactly1000Players() {
        // given
        Issue issue = createIssue(1000);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(1000L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("경계값 999명은 18점을 반환한다")
    void calculate999Players() {
        // given
        Issue issue = createIssue(999);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(999L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(18);
    }

    @Test
    @DisplayName("경계값 500명 정확히는 18점을 반환한다")
    void calculateExactly500Players() {
        // given
        Issue issue = createIssue(500);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(500L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(18);
    }

    @Test
    @DisplayName("경계값 100명 정확히는 15점을 반환한다")
    void calculateExactly100Players() {
        // given
        Issue issue = createIssue(100);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(100L);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(15);
    }

    @Test
    @DisplayName("getFactor()는 PLAYER_COUNT를 반환한다")
    void getFactor() {
        // when
        SeverityFactor factor = strategy.getFactor();

        // then
        assertThat(factor).isEqualTo(SeverityFactor.PLAYER_COUNT);
    }

    @Test
    @DisplayName("generateReason()은 점수가 0이면 null을 반환한다")
    void generateReasonWithZeroScore() {
        // given
        Issue issue = createIssue(0);
        GameLog log = createGameLog();

        // when
        String reason = strategy.generateReason(0, issue, log);

        // then
        assertThat(reason).isNull();
    }

    @Test
    @DisplayName("generateReason()은 플레이어 수와 점수를 포함한 텍스트를 반환한다")
    void generateReasonWithScore() {
        // given
        Issue issue = createIssue(1234);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(1234L);

        // when
        String reason = strategy.generateReason(20, issue, log);

        // then
        assertThat(reason).contains("플레이어");
        assertThat(reason).contains("1,234"); // 숫자 포맷팅 확인
        assertThat(reason).contains("영향");
        assertThat(reason).contains("20점");
    }

    @Test
    @DisplayName("generateReason()은 소수 플레이어도 포맷팅하여 표시한다")
    void generateReasonWithSmallNumber() {
        // given
        Issue issue = createIssue(5);
        GameLog log = createGameLog();
        given(userCountTracker.getAffectedUserCount(issue.getFingerprint())).willReturn(5L);

        // when
        String reason = strategy.generateReason(5, issue, log);

        // then
        assertThat(reason).contains("플레이어");
        assertThat(reason).contains("5");
        assertThat(reason).contains("영향");
        assertThat(reason).contains("5점");
    }

    // 테스트 헬퍼 메서드
    private Issue createIssue(int occurrenceCount) {
        return Issue.builder()
                .projectId(UUID.randomUUID())
                .fingerprint("test-fingerprint")
                .title("Test Issue")
                .errorType(ErrorType.UNKNOWN)
                .status(IssueStatus.TODO)
                .occurrenceCount(occurrenceCount)
                .firstOccurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .lastOccurredAt(OffsetDateTime.now(ZoneOffset.UTC))
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

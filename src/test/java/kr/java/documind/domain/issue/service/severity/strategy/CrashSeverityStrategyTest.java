package kr.java.documind.domain.issue.service.severity.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
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

@DisplayName("CrashSeverityStrategy 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrashSeverityStrategyTest {

    @Mock private SeverityProperties severityProperties;

    @InjectMocks private CrashSeverityStrategy strategy;

    @BeforeEach
    void setUp() {
        SeverityProperties.CrashConfig crashConfig = new SeverityProperties.CrashConfig();

        crashConfig.setErrorTypes(
                Map.ofEntries(
                        Map.entry("OUT_OF_MEMORY", 50),
                        Map.entry("STACK_OVERFLOW", 50),
                        Map.entry("DATABASE", 40),
                        Map.entry("DEADLOCK", 40),
                        Map.entry("NETWORK", 30),
                        Map.entry("TIMEOUT", 30),
                        Map.entry("IO", 30),
                        Map.entry("NULL_POINTER", 15),
                        Map.entry("INDEX_OUT_OF_BOUNDS", 15),
                        Map.entry("ILLEGAL_ARGUMENT", 10),
                        Map.entry("ILLEGAL_STATE", 10),
                        Map.entry("UNSUPPORTED_OPERATION", 10)));

        given(severityProperties.getCrash()).willReturn(crashConfig);
    }

    @Test
    @DisplayName("OUT_OF_MEMORY 에러는 50점을 반환한다")
    void calculateOutOfMemoryScore() {
        // given
        Issue issue = createIssue(ErrorType.OUT_OF_MEMORY);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(50);
    }

    @Test
    @DisplayName("STACK_OVERFLOW 에러는 50점을 반환한다")
    void calculateStackOverflowScore() {
        // given
        Issue issue = createIssue(ErrorType.STACK_OVERFLOW);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(50);
    }

    @Test
    @DisplayName("DATABASE 에러는 40점을 반환한다")
    void calculateDatabaseScore() {
        // given
        Issue issue = createIssue(ErrorType.DATABASE);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(40);
    }

    @Test
    @DisplayName("DEADLOCK 에러는 40점을 반환한다")
    void calculateDeadlockScore() {
        // given
        Issue issue = createIssue(ErrorType.DEADLOCK);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(40);
    }

    @Test
    @DisplayName("NETWORK 에러는 30점을 반환한다")
    void calculateNetworkScore() {
        // given
        Issue issue = createIssue(ErrorType.NETWORK);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(30);
    }

    @Test
    @DisplayName("NULL_POINTER 에러는 15점을 반환한다")
    void calculateNullPointerScore() {
        // given
        Issue issue = createIssue(ErrorType.NULL_POINTER);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(15);
    }

    @Test
    @DisplayName("ILLEGAL_ARGUMENT 에러는 10점을 반환한다")
    void calculateIllegalArgumentScore() {
        // given
        Issue issue = createIssue(ErrorType.ILLEGAL_ARGUMENT);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(10);
    }

    @Test
    @DisplayName("UNKNOWN 에러는 0점을 반환한다")
    void calculateUnknownScore() {
        // given
        Issue issue = createIssue(ErrorType.UNKNOWN);
        GameLog log = createGameLog();

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("getFactor()는 CRASH를 반환한다")
    void getFactor() {
        // when
        SeverityFactor factor = strategy.getFactor();

        // then
        assertThat(factor).isEqualTo(SeverityFactor.CRASH);
    }

    @Test
    @DisplayName("generateReason()은 점수가 0이면 null을 반환한다")
    void generateReasonWithZeroScore() {
        // given
        Issue issue = createIssue(ErrorType.UNKNOWN);
        GameLog log = createGameLog();

        // when
        String reason = strategy.generateReason(0, issue, log);

        // then
        assertThat(reason).isNull();
    }

    @Test
    @DisplayName("generateReason()은 점수가 50이면 '치명적 크래시' 텍스트를 반환한다")
    void generateReasonWith50Score() {
        // given
        Issue issue = createIssue(ErrorType.OUT_OF_MEMORY);
        GameLog log = createGameLog();

        // when
        String reason = strategy.generateReason(50, issue, log);

        // then
        assertThat(reason).contains("치명적 크래시");
        assertThat(reason).contains("50점");
    }

    @Test
    @DisplayName("generateReason()은 점수가 40이면 '서버 크래시 위험' 텍스트를 반환한다")
    void generateReasonWith40Score() {
        // given
        Issue issue = createIssue(ErrorType.DATABASE);
        GameLog log = createGameLog();

        // when
        String reason = strategy.generateReason(40, issue, log);

        // then
        assertThat(reason).contains("서버 크래시 위험");
        assertThat(reason).contains("40점");
    }

    // 테스트 헬퍼 메서드
    private Issue createIssue(ErrorType errorType) {
        return Issue.builder()
                .projectId(UUID.randomUUID())
                .fingerprint("test-fingerprint")
                .title("Test Issue")
                .errorType(errorType)
                .status(IssueStatus.TODO)
                .occurrenceCount(1)
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

package kr.java.documind.domain.issue.service.severity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.issue.model.vo.SeverityScore;
import kr.java.documind.domain.issue.service.severity.strategy.SeverityStrategy;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("SeverityCalculator 단위 테스트")
@ExtendWith(MockitoExtension.class)
class SeverityCalculatorTest {

    @Mock private SeverityStrategy crashStrategy;
    @Mock private SeverityStrategy frequencyStrategy;
    @Mock private SeverityStrategy blockingStrategy;
    @Mock private SeverityStrategy businessImpactStrategy;
    @Mock private SeverityStrategy playerCountStrategy;

    private SeverityCalculator severityCalculator;

    @BeforeEach
    void setUp() {
        List<SeverityStrategy> strategies =
                List.of(
                        crashStrategy,
                        frequencyStrategy,
                        blockingStrategy,
                        businessImpactStrategy,
                        playerCountStrategy);
        severityCalculator = new SeverityCalculator(strategies);

        // Mock 기본 동작 설정
        given(crashStrategy.getFactor()).willReturn(SeverityFactor.CRASH);
        given(frequencyStrategy.getFactor()).willReturn(SeverityFactor.FREQUENCY);
        given(blockingStrategy.getFactor()).willReturn(SeverityFactor.BLOCKING);
        given(businessImpactStrategy.getFactor()).willReturn(SeverityFactor.BUSINESS_IMPACT);
        given(playerCountStrategy.getFactor()).willReturn(SeverityFactor.PLAYER_COUNT);
    }

    @Test
    @DisplayName("모든 Strategy가 실행되고 점수가 합산된다")
    void calculateAllStrategies() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(50);
        given(frequencyStrategy.calculate(any(), any())).willReturn(20);
        given(blockingStrategy.calculate(any(), any())).willReturn(15);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(30);
        given(playerCountStrategy.calculate(any(), any())).willReturn(10);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        verify(crashStrategy, times(1)).calculate(issue, log);
        verify(frequencyStrategy, times(1)).calculate(issue, log);
        verify(blockingStrategy, times(1)).calculate(issue, log);
        verify(businessImpactStrategy, times(1)).calculate(issue, log);
        verify(playerCountStrategy, times(1)).calculate(issue, log);

        assertThat(result.getRawScore()).isEqualTo(125); // 50+20+15+30+10
        assertThat(result.getTotalScore()).isEqualTo(100); // 100점 캡핑
    }

    @Test
    @DisplayName("scoreBreakdown이 올바르게 생성된다")
    void scoreBreakdownIsCorrect() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(50);
        given(frequencyStrategy.calculate(any(), any())).willReturn(20);
        given(blockingStrategy.calculate(any(), any())).willReturn(0);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(30);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getScoreBreakdown()).hasSize(5);
        assertThat(result.getScoreBreakdown().get(SeverityFactor.CRASH)).isEqualTo(50);
        assertThat(result.getScoreBreakdown().get(SeverityFactor.FREQUENCY)).isEqualTo(20);
        assertThat(result.getScoreBreakdown().get(SeverityFactor.BLOCKING)).isEqualTo(0);
        assertThat(result.getScoreBreakdown().get(SeverityFactor.BUSINESS_IMPACT)).isEqualTo(30);
        assertThat(result.getScoreBreakdown().get(SeverityFactor.PLAYER_COUNT)).isEqualTo(0);
    }

    @Test
    @DisplayName("100점 미만일 때 totalScore와 rawScore가 동일하다")
    void totalScoreEqualsRawScoreWhenUnder100() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(30);
        given(frequencyStrategy.calculate(any(), any())).willReturn(15);
        given(blockingStrategy.calculate(any(), any())).willReturn(10);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(20);
        given(playerCountStrategy.calculate(any(), any())).willReturn(5);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getRawScore()).isEqualTo(80);
        assertThat(result.getTotalScore()).isEqualTo(80);
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.HIGH); // 60-89점
    }

    @Test
    @DisplayName("100점 초과 시 totalScore가 100으로 캡핑된다")
    void totalScoreCappedAt100() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(50);
        given(frequencyStrategy.calculate(any(), any())).willReturn(20);
        given(blockingStrategy.calculate(any(), any())).willReturn(20);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(30);
        given(playerCountStrategy.calculate(any(), any())).willReturn(20);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getRawScore()).isEqualTo(140);
        assertThat(result.getTotalScore()).isEqualTo(100);
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.CRITICAL); // 90-100점
    }

    @Test
    @DisplayName("점수가 90점 이상이면 CRITICAL 등급이 된다")
    void criticalSeverityWhen90OrMore() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(50);
        given(frequencyStrategy.calculate(any(), any())).willReturn(20);
        given(blockingStrategy.calculate(any(), any())).willReturn(20);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(0);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getTotalScore()).isEqualTo(90);
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.CRITICAL);
    }

    @Test
    @DisplayName("점수가 60-89점이면 HIGH 등급이 된다")
    void highSeverityWhen60To89() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(30);
        given(frequencyStrategy.calculate(any(), any())).willReturn(15);
        given(blockingStrategy.calculate(any(), any())).willReturn(15);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(0);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getTotalScore()).isEqualTo(60);
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.HIGH);
    }

    @Test
    @DisplayName("점수가 30-59점이면 MEDIUM 등급이 된다")
    void mediumSeverityWhen30To59() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(15);
        given(frequencyStrategy.calculate(any(), any())).willReturn(10);
        given(blockingStrategy.calculate(any(), any())).willReturn(5);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(0);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getTotalScore()).isEqualTo(30);
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
    }

    @Test
    @DisplayName("점수가 0-29점이면 LOW 등급이 된다")
    void lowSeverityWhen0To29() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(10);
        given(frequencyStrategy.calculate(any(), any())).willReturn(5);
        given(blockingStrategy.calculate(any(), any())).willReturn(0);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(0);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getTotalScore()).isEqualTo(15);
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.LOW);
    }

    @Test
    @DisplayName("점수가 0점이면 reason이 생성되지 않는다")
    void noReasonWhenAllZero() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(0);
        given(frequencyStrategy.calculate(any(), any())).willReturn(0);
        given(blockingStrategy.calculate(any(), any())).willReturn(0);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(0);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // 점수가 0일 때는 generateReason이 호출되지 않으므로 stub 제거

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getTotalScore()).isEqualTo(0);
        assertThat(result.getReason()).isEqualTo("점수 없음");
    }

    @Test
    @DisplayName("일부 Strategy만 점수가 있을 때 reason이 결합된다")
    void reasonCombinedWhenPartialScore() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(50);
        given(frequencyStrategy.calculate(any(), any())).willReturn(20);
        given(blockingStrategy.calculate(any(), any())).willReturn(0);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(30);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        given(crashStrategy.generateReason(50, issue, log)).willReturn("치명적 크래시 (50점)");
        given(frequencyStrategy.generateReason(20, issue, log)).willReturn("시간당 1,000회 발생 (20점)");
        given(businessImpactStrategy.generateReason(30, issue, log)).willReturn("결제 시스템 영향 (30점)");

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getReason()).contains("치명적 크래시");
        assertThat(result.getReason()).contains("시간당 1,000회 발생");
        assertThat(result.getReason()).contains("결제 시스템 영향");
        assertThat(result.getReason()).doesNotContain("(0점)");
    }

    // ===== 경계값 테스트 =====

    @Test
    @DisplayName("경계값: 89점은 HIGH 등급이다")
    void boundaryTest89IsHigh() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(50);
        given(frequencyStrategy.calculate(any(), any())).willReturn(20);
        given(blockingStrategy.calculate(any(), any())).willReturn(19);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(0);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getTotalScore()).isEqualTo(89);
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.HIGH);
    }

    @Test
    @DisplayName("경계값: 90점은 CRITICAL 등급이다")
    void boundaryTest90IsCritical() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(50);
        given(frequencyStrategy.calculate(any(), any())).willReturn(20);
        given(blockingStrategy.calculate(any(), any())).willReturn(20);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(0);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getTotalScore()).isEqualTo(90);
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.CRITICAL);
    }

    @Test
    @DisplayName("경계값: 59점은 MEDIUM 등급이다")
    void boundaryTest59IsMedium() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(30);
        given(frequencyStrategy.calculate(any(), any())).willReturn(15);
        given(blockingStrategy.calculate(any(), any())).willReturn(14);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(0);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getTotalScore()).isEqualTo(59);
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
    }

    @Test
    @DisplayName("경계값: 60점은 HIGH 등급이다")
    void boundaryTest60IsHigh() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(30);
        given(frequencyStrategy.calculate(any(), any())).willReturn(15);
        given(blockingStrategy.calculate(any(), any())).willReturn(15);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(0);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getTotalScore()).isEqualTo(60);
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.HIGH);
    }

    @Test
    @DisplayName("경계값: 29점은 LOW 등급이다")
    void boundaryTest29IsLow() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(15);
        given(frequencyStrategy.calculate(any(), any())).willReturn(10);
        given(blockingStrategy.calculate(any(), any())).willReturn(4);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(0);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getTotalScore()).isEqualTo(29);
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.LOW);
    }

    @Test
    @DisplayName("경계값: 30점은 MEDIUM 등급이다")
    void boundaryTest30IsMedium() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(15);
        given(frequencyStrategy.calculate(any(), any())).willReturn(10);
        given(blockingStrategy.calculate(any(), any())).willReturn(5);
        given(businessImpactStrategy.calculate(any(), any())).willReturn(0);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getTotalScore()).isEqualTo(30);
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
    }

    // ===== 키워드 중복 테스트 =====

    @Test
    @DisplayName("동일 키워드가 여러 Strategy에서 감지되어도 각 Strategy는 독립적으로 점수를 계산한다")
    void sameKeywordInMultipleStrategies() {
        // given
        // "메인" 키워드가 BusinessImpactStrategy(15점)와 BlockingStrategy(15점) 모두에서 감지
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(0);
        given(frequencyStrategy.calculate(any(), any())).willReturn(0);
        given(blockingStrategy.calculate(any(), any())).willReturn(15); // "메인" 키워드
        given(businessImpactStrategy.calculate(any(), any())).willReturn(15); // "메인" 키워드
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        // 각 Strategy가 독립적으로 점수를 계산하므로 15 + 15 = 30점
        assertThat(result.getTotalScore()).isEqualTo(30);
        assertThat(result.getScoreBreakdown().get(SeverityFactor.BLOCKING)).isEqualTo(15);
        assertThat(result.getScoreBreakdown().get(SeverityFactor.BUSINESS_IMPACT)).isEqualTo(15);
    }

    @Test
    @DisplayName("동일 ErrorType이 여러 Strategy에서 평가되어도 각 Strategy는 독립적으로 점수를 계산한다")
    void sameErrorTypeInMultipleStrategies() {
        // given
        // NETWORK ErrorType이 CrashStrategy(30점)와 BlockingStrategy(20점)에서 평가
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(30); // NETWORK
        given(frequencyStrategy.calculate(any(), any())).willReturn(0);
        given(blockingStrategy.calculate(any(), any())).willReturn(20); // NETWORK
        given(businessImpactStrategy.calculate(any(), any())).willReturn(0);
        given(playerCountStrategy.calculate(any(), any())).willReturn(0);

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        // 각 Strategy가 독립적으로 점수를 계산하므로 30 + 20 = 50점
        assertThat(result.getTotalScore()).isEqualTo(50);
        assertThat(result.getScoreBreakdown().get(SeverityFactor.CRASH)).isEqualTo(30);
        assertThat(result.getScoreBreakdown().get(SeverityFactor.BLOCKING)).isEqualTo(20);
    }

    @Test
    @DisplayName("모든 Strategy에서 최대 점수가 나와도 100점으로 캡핑된다")
    void allStrategiesMaxScoreCapped() {
        // given
        Issue issue = createIssue();
        GameLog log = createGameLog();

        given(crashStrategy.calculate(any(), any())).willReturn(50); // 최대
        given(frequencyStrategy.calculate(any(), any())).willReturn(20); // 최대
        given(blockingStrategy.calculate(any(), any())).willReturn(20); // 최대
        given(businessImpactStrategy.calculate(any(), any())).willReturn(30); // 최대
        given(playerCountStrategy.calculate(any(), any())).willReturn(20); // 최대

        // when
        SeverityScore result = severityCalculator.calculate(issue, log);

        // then
        assertThat(result.getRawScore()).isEqualTo(140); // 50+20+20+30+20
        assertThat(result.getTotalScore()).isEqualTo(100); // 캡핑
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.CRITICAL);
    }

    // 테스트 헬퍼 메서드
    private Issue createIssue() {
        return Issue.builder()
                .projectId(UUID.randomUUID())
                .fingerprint("test-fingerprint")
                .title("Test Issue")
                .errorType(ErrorType.UNKNOWN)
                .status(IssueStatus.TODO)
                .occurrenceCount(100)
                .firstOccurredAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
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

package kr.java.documind.domain.issue.service.severity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssuePriority;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.enums.IssueType;
import kr.java.documind.domain.issue.model.vo.SeverityScore;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.enums.EventCategory;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueSeverityService 단위 테스트")
class IssueSeverityServiceTest {

    @Mock
    private SeverityCalculator severityCalculator;

    @InjectMocks
    private IssueSeverityService issueSeverityService;

    private Issue issue;
    private GameLog gameLog;

    @BeforeEach
    void setUp() {
        issue = Issue.builder()
                .projectId(UUID.randomUUID())
                .fingerprint("test-fingerprint-001")
                .title("NullPointerException in PlayerService")
                .stackKey("PlayerService.java:loadPlayer:42")
                .issueType(IssueType.BUG)
                .errorType(ErrorType.NULL_POINTER)
                .status(IssueStatus.RECOMMENDED)
                .priority(IssuePriority.P2)
                .severity(IssueSeverity.MEDIUM)
                .severityScore(50)
                .occurrenceCount(1)
                .firstOccurredAt(OffsetDateTime.now())
                .lastOccurredAt(OffsetDateTime.now())
                .build();

        gameLog = GameLog.builder()
                .logId(UUID.randomUUID())
                .projectId(issue.getProjectId())
                .sessionId("test-session")
                .severity(LogSeverity.ERROR)
                .eventCategory(EventCategory.SYSTEM)
                .archive("java.lang.NullPointerException: Cannot load player")
                .occurredAt(OffsetDateTime.now())
                .ingestedAt(OffsetDateTime.now())
                .fingerprint("test-fingerprint-001")
                .resource(Map.of())
                .attributes(Map.of())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("CRITICAL 등급 판별: 게임 크래시 + 다수 플레이어 영향")
    void calculateCriticalSeverity() {
        // Given
        SeverityScore criticalScore = new SeverityScore(
                IssueSeverity.CRITICAL,
                100, // totalScore
                110, // rawScore (cap at 100)
                "게임 크래시 (50점) + 플레이어 1500명 (20점) + 결제 시스템 (30점)"
        );

        when(severityCalculator.calculate(any(Issue.class), any(GameLog.class)))
                .thenReturn(criticalScore);

        // When
        SeverityScore result = issueSeverityService.calculateAndUpdateSeverity(issue, gameLog);

        // Then
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.CRITICAL);
        assertThat(result.getTotalScore()).isEqualTo(100);
        assertThat(result.getRawScore()).isEqualTo(110);

        // Issue 엔티티도 업데이트되었는지 확인
        assertThat(issue.getSeverity()).isEqualTo(IssueSeverity.CRITICAL);
        assertThat(issue.getSeverityScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("HIGH 등급 판별: 메인 진행 차단")
    void calculateHighSeverity() {
        // Given
        SeverityScore highScore = new SeverityScore(
                IssueSeverity.HIGH,
                75,
                75,
                "메인 퀘스트 진행 차단 (15점) + 플레이어 500명 (18점)"
        );

        when(severityCalculator.calculate(any(Issue.class), any(GameLog.class)))
                .thenReturn(highScore);

        // When
        SeverityScore result = issueSeverityService.calculateAndUpdateSeverity(issue, gameLog);

        // Then
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.HIGH);
        assertThat(result.getTotalScore()).isEqualTo(75);

        assertThat(issue.getSeverity()).isEqualTo(IssueSeverity.HIGH);
        assertThat(issue.getSeverityScore()).isEqualTo(75);
    }

    @Test
    @DisplayName("MEDIUM 등급 판별: 일부 기능 차단")
    void calculateMediumSeverity() {
        // Given
        SeverityScore mediumScore = new SeverityScore(
                IssueSeverity.MEDIUM,
                45,
                45,
                "일부 기능 차단 (10점) + 플레이어 30명 (8점)"
        );

        when(severityCalculator.calculate(any(Issue.class), any(GameLog.class)))
                .thenReturn(mediumScore);

        // When
        SeverityScore result = issueSeverityService.calculateAndUpdateSeverity(issue, gameLog);

        // Then
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
        assertThat(result.getTotalScore()).isEqualTo(45);

        assertThat(issue.getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
        assertThat(issue.getSeverityScore()).isEqualTo(45);
    }

    @Test
    @DisplayName("LOW 등급 판별: 경미한 UI 버그")
    void calculateLowSeverity() {
        // Given
        SeverityScore lowScore = new SeverityScore(
                IssueSeverity.LOW,
                17,
                17,
                "UI 렌더링 버그 (15점) + 플레이어 3명 (2점)"
        );

        when(severityCalculator.calculate(any(Issue.class), any(GameLog.class)))
                .thenReturn(lowScore);

        // When
        SeverityScore result = issueSeverityService.calculateAndUpdateSeverity(issue, gameLog);

        // Then
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.LOW);
        assertThat(result.getTotalScore()).isEqualTo(17);

        assertThat(issue.getSeverity()).isEqualTo(IssueSeverity.LOW);
        assertThat(issue.getSeverityScore()).isEqualTo(17);
    }

    @Test
    @DisplayName("조회 전용 심각도 계산: Issue 엔티티 업데이트 없음")
    void calculateSeverityOnlyWithoutUpdate() {
        // Given
        IssueSeverity originalSeverity = issue.getSeverity();
        Integer originalScore = issue.getSeverityScore();

        SeverityScore calculatedScore = new SeverityScore(
                IssueSeverity.CRITICAL,
                90,
                90,
                "계산된 심각도"
        );

        when(severityCalculator.calculate(any(Issue.class), any(GameLog.class)))
                .thenReturn(calculatedScore);

        // When
        SeverityScore result = issueSeverityService.calculateSeverityOnly(issue, gameLog);

        // Then
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.CRITICAL);
        assertThat(result.getTotalScore()).isEqualTo(90);

        // Issue 엔티티는 변경되지 않아야 함
        assertThat(issue.getSeverity()).isEqualTo(originalSeverity);
        assertThat(issue.getSeverityScore()).isEqualTo(originalScore);
    }

    @Test
    @DisplayName("예외: Issue가 null일 때 IllegalArgumentException 발생")
    void throwExceptionWhenIssueIsNull() {
        // Given
        Issue nullIssue = null;

        // When & Then
        assertThatThrownBy(() ->
                issueSeverityService.calculateAndUpdateSeverity(nullIssue, gameLog))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Issue는 null일 수 없습니다.");
    }

    @Test
    @DisplayName("예외: GameLog가 null일 때 IllegalArgumentException 발생")
    void throwExceptionWhenGameLogIsNull() {
        // Given
        GameLog nullGameLog = null;

        // When & Then
        assertThatThrownBy(() ->
                issueSeverityService.calculateAndUpdateSeverity(issue, nullGameLog))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("GameLog는 null일 수 없습니다.");
    }

    @Test
    @DisplayName("심각도 재계산: 발생 횟수 증가 시 심각도 재평가")
    void recalculateSeverityOnOccurrenceIncrease() {
        // Given: 초기 심각도 MEDIUM (50점)
        issue.updateSeverity(IssueSeverity.MEDIUM, 50);

        // 발생 횟수 증가 후 재계산 시 HIGH로 상승
        SeverityScore updatedScore = new SeverityScore(
                IssueSeverity.HIGH,
                75,
                75,
                "발생 빈도 증가 (20점 추가)"
        );

        when(severityCalculator.calculate(any(Issue.class), any(GameLog.class)))
                .thenReturn(updatedScore);

        // When
        SeverityScore result = issueSeverityService.calculateAndUpdateSeverity(issue, gameLog);

        // Then
        assertThat(result.getSeverity()).isEqualTo(IssueSeverity.HIGH);
        assertThat(result.getTotalScore()).isEqualTo(75);

        // Issue도 업데이트됨
        assertThat(issue.getSeverity()).isEqualTo(IssueSeverity.HIGH);
        assertThat(issue.getSeverityScore()).isEqualTo(75);
    }
}

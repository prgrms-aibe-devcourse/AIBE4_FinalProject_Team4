package kr.java.documind.domain.issue.service.severity.strategy;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BusinessImpactStrategy 단위 테스트")
class BusinessImpactStrategyTest {

    private final BusinessImpactStrategy strategy = new BusinessImpactStrategy();

    @Test
    @DisplayName("제목에 '결제' 키워드가 포함되면 30점을 반환한다")
    void calculatePaymentKeywordInTitle() {
        // given
        Issue issue = createIssue("결제 실패", null);
        GameLog log = createGameLog("Error log", null);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(30);
    }

    @Test
    @DisplayName("제목에 'payment' 키워드가 포함되면 30점을 반환한다")
    void calculatePaymentKeywordInTitleEnglish() {
        // given
        Issue issue = createIssue("Payment Failed", null);
        GameLog log = createGameLog("Error log", null);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(30);
    }

    @Test
    @DisplayName("제목에 '인앱' 키워드가 포함되면 30점을 반환한다")
    void calculateIapKeywordInTitle() {
        // given
        Issue issue = createIssue("인앱 결제 오류", null);
        GameLog log = createGameLog("Error log", null);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(30);
    }

    @Test
    @DisplayName("설명에 '가챠' 키워드가 포함되면 25점을 반환한다")
    void calculateGachaKeywordInDescription() {
        // given
        Issue issue = createIssue("게임 오류", "가챠 뽑기 실패");
        GameLog log = createGameLog("Error log", null);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(25);
    }

    @Test
    @DisplayName("설명에 '상점' 키워드가 포함되면 25점을 반환한다")
    void calculateShopKeywordInDescription() {
        // given
        Issue issue = createIssue("게임 오류", "상점 접속 불가");
        GameLog log = createGameLog("Error log", null);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(25);
    }

    @Test
    @DisplayName("아카이브에 '보스' 키워드가 포함되면 20점을 반환한다")
    void calculateBossKeywordInArchive() {
        // given
        Issue issue = createIssue("게임 오류", null);
        GameLog log = createGameLog("보스 레이드 버그", null);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("아카이브에 'raid' 키워드가 포함되면 20점을 반환한다")
    void calculateRaidKeywordInArchive() {
        // given
        Issue issue = createIssue("게임 오류", null);
        GameLog log = createGameLog("Raid boss error", null);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("아카이브에 '아이템' 키워드가 포함되면 15점을 반환한다")
    void calculateItemKeywordInArchive() {
        // given
        Issue issue = createIssue("게임 오류", null);
        GameLog log = createGameLog("아이템 중복 지급", null);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(15);
    }

    @Test
    @DisplayName("아카이브에 '퀘스트' 키워드가 포함되면 15점을 반환한다")
    void calculateQuestKeywordInArchive() {
        // given
        Issue issue = createIssue("게임 오류", null);
        GameLog log = createGameLog("Quest completion failed", null);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(15);
    }

    @Test
    @DisplayName("attributes에 '길드' 키워드가 포함되면 10점을 반환한다")
    void calculateGuildKeywordInAttributes() {
        // given
        Issue issue = createIssue("게임 오류", null);
        GameLog log = createGameLog("Error log", Map.of("feature", "길드 시스템"));

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(10);
    }

    @Test
    @DisplayName("attributes에 'friend' 키워드가 포함되면 10점을 반환한다")
    void calculateFriendKeywordInAttributes() {
        // given
        Issue issue = createIssue("게임 오류", null);
        GameLog log = createGameLog("Error log", Map.of("module", "friend_invite"));

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(10);
    }

    @Test
    @DisplayName("제목에 '튜토리얼' 키워드가 포함되면 5점을 반환한다")
    void calculateTutorialKeywordInTitle() {
        // given
        Issue issue = createIssue("튜토리얼 버그", null);
        GameLog log = createGameLog("Error log", null);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(5);
    }

    @Test
    @DisplayName("여러 키워드가 감지되면 최대 점수를 반환한다")
    void calculateMultipleKeywords() {
        // given
        Issue issue = createIssue("결제 오류", "가챠 뽑기 실패");
        GameLog log = createGameLog("보스 레이드 버그", Map.of("feature", "길드"));

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(30); // '결제' 키워드가 최대값
    }

    @Test
    @DisplayName("어떤 키워드도 감지되지 않으면 0점을 반환한다")
    void calculateNoKeyword() {
        // given
        Issue issue = createIssue("일반 오류", null);
        GameLog log = createGameLog("Generic error", null);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("빈 문자열에서는 키워드를 감지하지 않는다")
    void calculateEmptyStrings() {
        // given
        Issue issue = createIssue("", "");
        GameLog log = createGameLog("", null);

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("getFactor()는 BUSINESS_IMPACT를 반환한다")
    void getFactor() {
        // when
        SeverityFactor factor = strategy.getFactor();

        // then
        assertThat(factor).isEqualTo(SeverityFactor.BUSINESS_IMPACT);
    }

    @Test
    @DisplayName("generateReason()은 점수가 0이면 null을 반환한다")
    void generateReasonWithZeroScore() {
        // given
        Issue issue = createIssue("일반 오류", null);
        GameLog log = createGameLog("Error", null);

        // when
        String reason = strategy.generateReason(0, issue, log);

        // then
        assertThat(reason).isNull();
    }

    @Test
    @DisplayName("generateReason()은 점수 30이면 '결제 시스템 영향' 텍스트를 반환한다")
    void generateReasonWith30Score() {
        // given
        Issue issue = createIssue("결제 실패", null);
        GameLog log = createGameLog("Error", null);

        // when
        String reason = strategy.generateReason(30, issue, log);

        // then
        assertThat(reason).contains("결제 시스템 영향");
        assertThat(reason).contains("30점");
    }

    @Test
    @DisplayName("generateReason()은 점수 25이면 '가챠/상점 영향' 텍스트를 반환한다")
    void generateReasonWith25Score() {
        // given
        Issue issue = createIssue("가챠 오류", null);
        GameLog log = createGameLog("Error", null);

        // when
        String reason = strategy.generateReason(25, issue, log);

        // then
        assertThat(reason).contains("가챠/상점 영향");
        assertThat(reason).contains("25점");
    }

    @Test
    @DisplayName("generateReason()은 점수 20이면 '게임 밸런스 영향' 텍스트를 반환한다")
    void generateReasonWith20Score() {
        // given
        Issue issue = createIssue("보스 버그", null);
        GameLog log = createGameLog("Error", null);

        // when
        String reason = strategy.generateReason(20, issue, log);

        // then
        assertThat(reason).contains("게임 밸런스 영향");
        assertThat(reason).contains("20점");
    }

    // 테스트 헬퍼 메서드
    private Issue createIssue(String title, String description) {
        return Issue.builder()
                .projectId(UUID.randomUUID())
                .fingerprint("test-fingerprint")
                .title(title)
                .description(description)
                .errorType(ErrorType.UNKNOWN)
                .status(IssueStatus.TODO)
                .occurrenceCount(1)
                .firstOccurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .lastOccurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private GameLog createGameLog(String archive, Map<String, Object> attributes) {
        return GameLog.builder()
                .projectId(UUID.randomUUID())
                .fingerprint("test-fingerprint")
                .archive(archive)
                .attributes(attributes)
                .severity(LogSeverity.ERROR)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}

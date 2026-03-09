package kr.java.documind.domain.issue.service.severity.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BlockingStrategy 단위 테스트")
class BlockingStrategyTest {

    private final BlockingStrategy strategy = new BlockingStrategy();

    @Test
    @DisplayName("AUTHENTICATION 에러는 20점을 반환한다")
    void calculateAuthenticationError() {
        // given
        Issue issue = createIssue(ErrorType.AUTHENTICATION, "로그인 실패", null);
        GameLog log = createGameLog("Authentication failed");

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("NETWORK 에러는 20점을 반환한다")
    void calculateNetworkError() {
        // given
        Issue issue = createIssue(ErrorType.NETWORK, "서버 접속 불가", null);
        GameLog log = createGameLog("Server connection failed");

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("TIMEOUT 에러는 15점을 반환한다")
    void calculateTimeoutError() {
        // given
        Issue issue = createIssue(ErrorType.TIMEOUT, "타임아웃", null);
        GameLog log = createGameLog("Timeout occurred");

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(15);
    }

    @Test
    @DisplayName("IO 에러는 10점을 반환한다")
    void calculateIoError() {
        // given
        Issue issue = createIssue(ErrorType.IO, "파일 읽기 실패", null);
        GameLog log = createGameLog("IO error occurred");

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(10);
    }

    @Test
    @DisplayName("제목에 '로그인' 키워드가 포함되면 20점을 반환한다")
    void calculateLoginKeywordInTitle() {
        // given
        Issue issue = createIssue(ErrorType.UNKNOWN, "로그인 에러 발생", null);
        GameLog log = createGameLog("Error log");

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("제목에 'login' 키워드가 포함되면 20점을 반환한다")
    void calculateLoginKeywordInTitleEnglish() {
        // given
        Issue issue = createIssue(ErrorType.UNKNOWN, "Login Failed", null);
        GameLog log = createGameLog("Error log");

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("설명에 '메인 퀘스트' 키워드가 포함되면 15점을 반환한다")
    void calculateMainQuestKeywordInDescription() {
        // given
        Issue issue = createIssue(ErrorType.UNKNOWN, "게임 오류", "메인 퀘스트 진행 불가");
        GameLog log = createGameLog("Error log");

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(15);
    }

    @Test
    @DisplayName("아카이브에 '보상' 키워드가 포함되면 10점을 반환한다")
    void calculateRewardKeywordInArchive() {
        // given
        Issue issue = createIssue(ErrorType.UNKNOWN, "오류", null);
        GameLog log = createGameLog("보상 수령 실패");

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(10);
    }

    @Test
    @DisplayName("아카이브에 'slow' 키워드가 포함되면 5점을 반환한다")
    void calculateSlowKeywordInArchive() {
        // given
        Issue issue = createIssue(ErrorType.UNKNOWN, "오류", null);
        GameLog log = createGameLog("Game is running slow");

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(5);
    }

    @Test
    @DisplayName("여러 키워드가 감지되면 최대 점수를 반환한다")
    void calculateMultipleKeywords() {
        // given
        Issue issue = createIssue(ErrorType.UNKNOWN, "로그인 버그", "메인 퀘스트도 오류");
        GameLog log = createGameLog("UI bug and slow performance");

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(20); // '로그인' 키워드가 최대값
    }

    @Test
    @DisplayName("ErrorType과 키워드가 모두 감지되면 최대 점수를 반환한다")
    void calculateErrorTypeAndKeyword() {
        // given
        Issue issue = createIssue(ErrorType.TIMEOUT, "메인 퀘스트 타임아웃", null);
        GameLog log = createGameLog("Timeout in quest");

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(15); // TIMEOUT(15), '메인'(15) 중 max
    }

    @Test
    @DisplayName("어떤 키워드도 감지되지 않고 ErrorType도 매칭되지 않으면 0점을 반환한다")
    void calculateNoMatch() {
        // given
        Issue issue = createIssue(ErrorType.UNKNOWN, "일반 오류", null);
        GameLog log = createGameLog("Generic error");

        // when
        int score = strategy.calculate(issue, log);

        // then
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("getFactor()는 BLOCKING을 반환한다")
    void getFactor() {
        // when
        SeverityFactor factor = strategy.getFactor();

        // then
        assertThat(factor).isEqualTo(SeverityFactor.BLOCKING);
    }

    @Test
    @DisplayName("generateReason()은 점수가 0이면 null을 반환한다")
    void generateReasonWithZeroScore() {
        // given
        Issue issue = createIssue(ErrorType.UNKNOWN, "일반 오류", null);
        GameLog log = createGameLog("Error");

        // when
        String reason = strategy.generateReason(0, issue, log);

        // then
        assertThat(reason).isNull();
    }

    @Test
    @DisplayName("generateReason()은 점수 20이면 '게임 완전 차단' 텍스트를 반환한다")
    void generateReasonWith20Score() {
        // given
        Issue issue = createIssue(ErrorType.AUTHENTICATION, "로그인 실패", null);
        GameLog log = createGameLog("Authentication failed");

        // when
        String reason = strategy.generateReason(20, issue, log);

        // then
        assertThat(reason).contains("게임 완전 차단");
        assertThat(reason).contains("20점");
    }

    @Test
    @DisplayName("generateReason()은 점수 15이면 '메인 진행 차단' 텍스트를 반환한다")
    void generateReasonWith15Score() {
        // given
        Issue issue = createIssue(ErrorType.TIMEOUT, "타임아웃", null);
        GameLog log = createGameLog("Timeout");

        // when
        String reason = strategy.generateReason(15, issue, log);

        // then
        assertThat(reason).contains("메인 진행 차단");
        assertThat(reason).contains("15점");
    }

    // 테스트 헬퍼 메서드
    private Issue createIssue(ErrorType errorType, String title, String description) {
        return Issue.builder()
                .projectId(UUID.randomUUID())
                .fingerprint("test-fingerprint")
                .title(title)
                .description(description)
                .errorType(errorType)
                .status(IssueStatus.TODO)
                .occurrenceCount(1)
                .firstOccurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .lastOccurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private GameLog createGameLog(String archive) {
        return GameLog.builder()
                .projectId(UUID.randomUUID())
                .fingerprint("test-fingerprint")
                .archive(archive)
                .severity(LogSeverity.ERROR)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}

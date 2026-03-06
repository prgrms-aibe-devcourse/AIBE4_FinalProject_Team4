package kr.java.documind.domain.issue.service.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.issue.model.enums.FingerprintQuality;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.enums.EventCategory;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FingerprintGenerator 테스트")
class FingerprintGeneratorTest {

    private FingerprintGenerator generator;
    private MessageNormalizer messageNormalizer;
    private StackFrameFilter stackFrameFilter;

    @BeforeEach
    void setUp() {
        messageNormalizer = new MessageNormalizer();
        stackFrameFilter = new StackFrameFilter();
        generator = new FingerprintGenerator(messageNormalizer, stackFrameFilter);
    }

    @Test
    @DisplayName("Strategy 1: 전체 스택트레이스 기반 핑거프린트 (HIGH quality)")
    void generateFullStacktraceFingerprint() {
        // given
        String archive =
                """
                java.lang.NullPointerException: Cannot invoke method on null object
                at kr.java.documind.service.PlayerService.loadPlayer(PlayerService.java:42)
                at kr.java.documind.controller.GameController.startGame(GameController.java:15)
                at kr.java.documind.service.InventoryService.initialize(InventoryService.java:28)
                """;

        GameLog log = createGameLog(archive);

        // when
        FingerprintResult result = generator.generate(log);

        // then
        assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        assertThat(result.getStrategy()).isEqualTo("Full Stacktrace");
        assertThat(result.getFingerprint()).hasSize(64); // SHA-256 hex
        assertThat(result.requiresReview()).isFalse();
    }

    @Test
    @DisplayName("Strategy 2: 부분 스택트레이스 기반 핑거프린트 (MEDIUM quality)")
    void generatePartialStacktraceFingerprint() {
        // given
        String archive =
                """
                java.lang.IllegalArgumentException: Invalid player ID
                at kr.java.documind.service.PlayerService.validate(PlayerService.java:10)
                at kr.java.documind.controller.PlayerController.create(PlayerController.java:20)
                """;

        GameLog log = createGameLog(archive);

        // when
        FingerprintResult result = generator.generate(log);

        // then
        assertThat(result.getQuality()).isEqualTo(FingerprintQuality.MEDIUM);
        assertThat(result.getStrategy()).isEqualTo("Partial Stacktrace");
        assertThat(result.getFingerprint()).hasSize(64);
        assertThat(result.requiresReview()).isFalse();
    }

    @Test
    @DisplayName("Strategy 3: 예외 타입 + 메시지 기반 핑거프린트 (LOW quality)")
    void generateExceptionMessageFingerprint() {
        // given
        String archive = "java.lang.IllegalStateException: Game session already started";

        GameLog log = createGameLog(archive);

        // when
        FingerprintResult result = generator.generate(log);

        // then
        assertThat(result.getQuality()).isEqualTo(FingerprintQuality.LOW);
        assertThat(result.getStrategy()).isEqualTo("Exception Type + Message");
        assertThat(result.getFingerprint()).hasSize(64);
        assertThat(result.requiresReview()).isTrue(); // 수동 검토 필요
    }

    @Test
    @DisplayName("Strategy 4: 메시지만 기반 핑거프린트 (VERY_LOW quality)")
    void generateMessageOnlyFingerprint() {
        // given
        String archive = "Connection timeout occurred";

        GameLog log = createGameLog(archive);

        // when
        FingerprintResult result = generator.generate(log);

        // then
        assertThat(result.getQuality()).isEqualTo(FingerprintQuality.VERY_LOW);
        assertThat(result.getStrategy()).isEqualTo("Message Only");
        assertThat(result.getFingerprint()).hasSize(64);
        assertThat(result.requiresReview()).isTrue(); // 수동 검토 필수
    }

    @Test
    @DisplayName("Strategy 5: 폴백 핑거프린트 (FALLBACK quality)")
    void generateFallbackFingerprint() {
        // given
        String archive = ""; // 메시지도 스택트레이스도 없음

        GameLog log = createGameLog(archive);

        // when
        FingerprintResult result = generator.generate(log);

        // then
        assertThat(result.getQuality()).isEqualTo(FingerprintQuality.FALLBACK);
        assertThat(result.getStrategy()).isEqualTo("Fallback (Severity + Category)");
        assertThat(result.getFingerprint()).hasSize(64);
        assertThat(result.requiresReview()).isTrue(); // 수동 검토 필수
    }

    @Test
    @DisplayName("동일한 에러는 동일한 핑거프린트 생성")
    void generateConsistentFingerprintForSameError() {
        // given
        String archive1 =
                """
                java.lang.NullPointerException: Cannot load player 12345
                at kr.java.documind.service.PlayerService.load(PlayerService.java:42)
                at kr.java.documind.controller.GameController.start(GameController.java:15)
                """;

        String archive2 =
                """
                java.lang.NullPointerException: Cannot load player 67890
                at kr.java.documind.service.PlayerService.load(PlayerService.java:44)
                at kr.java.documind.controller.GameController.start(GameController.java:16)
                """;

        GameLog log1 = createGameLog(archive1);
        GameLog log2 = createGameLog(archive2);

        // when
        FingerprintResult result1 = generator.generate(log1);
        FingerprintResult result2 = generator.generate(log2);

        // then - 동일한 에러 원인이므로 동일한 fingerprint
        assertThat(result1.getFingerprint()).isEqualTo(result2.getFingerprint());
        assertThat(result1.getQuality()).isEqualTo(result2.getQuality());
    }

    @Test
    @DisplayName("다른 에러는 다른 핑거프린트 생성")
    void generateDifferentFingerprintForDifferentErrors() {
        // given
        String archive1 =
                """
                java.lang.NullPointerException: Cannot load player
                at kr.java.documind.service.PlayerService.load(PlayerService.java:42)
                """;

        String archive2 =
                """
                java.lang.IllegalArgumentException: Invalid player ID
                at kr.java.documind.service.PlayerService.validate(PlayerService.java:10)
                """;

        GameLog log1 = createGameLog(archive1);
        GameLog log2 = createGameLog(archive2);

        // when
        FingerprintResult result1 = generator.generate(log1);
        FingerprintResult result2 = generator.generate(log2);

        // then - 다른 에러이므로 다른 fingerprint
        assertThat(result1.getFingerprint()).isNotEqualTo(result2.getFingerprint());
    }

    @Test
    @DisplayName("메시지 정규화로 동적 데이터가 제거되어 동일한 핑거프린트 생성")
    void normalizeMessageForConsistentFingerprint() {
        // given
        String archive1 = "java.lang.RuntimeException: Player player_12345 not found";
        String archive2 = "java.lang.RuntimeException: Player player_99999 not found";

        GameLog log1 = createGameLog(archive1);
        GameLog log2 = createGameLog(archive2);

        // when
        FingerprintResult result1 = generator.generate(log1);
        FingerprintResult result2 = generator.generate(log2);

        // then - player ID가 다르지만 정규화되어 동일한 fingerprint
        assertThat(result1.getFingerprint()).isEqualTo(result2.getFingerprint());
    }

    @Test
    @DisplayName("품질 등급에 따른 수동 검토 필요 여부 확인")
    void checkReviewRequirementByQuality() {
        // given
        String highQualityArchive =
                """
                java.lang.Exception: Error
                at kr.java.documind.service.A.method(A.java:1)
                at kr.java.documind.service.B.method(B.java:2)
                at kr.java.documind.service.C.method(C.java:3)
                """;
        String lowQualityArchive = "java.lang.Exception: Error message";
        String fallbackArchive = "";

        // when
        FingerprintResult highResult = generator.generate(createGameLog(highQualityArchive));
        FingerprintResult lowResult = generator.generate(createGameLog(lowQualityArchive));
        FingerprintResult fallbackResult = generator.generate(createGameLog(fallbackArchive));

        // then
        assertThat(highResult.requiresReview()).isFalse(); // HIGH/MEDIUM은 자동 그룹핑
        assertThat(lowResult.requiresReview()).isTrue(); // LOW는 수동 검토
        assertThat(fallbackResult.requiresReview()).isTrue(); // FALLBACK은 수동 검토
    }

    private GameLog createGameLog(String archive) {
        return GameLog.builder()
                .logId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .sessionId("test-session")
                .severity(LogSeverity.ERROR)
                .eventCategory(EventCategory.SYSTEM)
                .archive(archive)
                .occurredAt(OffsetDateTime.now())
                .ingestedAt(OffsetDateTime.now())
                .fingerprint("temp")
                .resource(Map.of())
                .attributes(Map.of())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}

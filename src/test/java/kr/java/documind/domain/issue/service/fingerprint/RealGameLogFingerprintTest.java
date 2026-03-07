package kr.java.documind.domain.issue.service.fingerprint;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import kr.java.documind.domain.issue.model.enums.FingerprintQuality;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.enums.EventCategory;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 실제 게임 로그를 사용한 Fingerprint 품질 검증 테스트
 *
 * <p>목적: 과거 가상 데이터가 아닌 실제 게임 로그로 fingerprint quality를 검증
 */
class RealGameLogFingerprintTest {

    private FingerprintGenerator generator;
    private MessageNormalizer messageNormalizer;
    private StackFrameFilter stackFrameFilter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        messageNormalizer = new MessageNormalizer();
        stackFrameFilter = new StackFrameFilter();
        generator = new FingerprintGenerator(messageNormalizer, stackFrameFilter);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("실제 게임 로그 파일들의 fingerprint quality 분석")
    void analyzeRealGameLogQuality() throws IOException {
        // Given: 실제 게임 로그 파일들 로드
        File logDirectory = new ClassPathResource("fixtures/logprocessor/game-logs").getFile();
        File[] logFiles = logDirectory.listFiles((dir, name) -> name.endsWith(".json"));

        assertThat(logFiles).isNotEmpty();

        Map<FingerprintQuality, Integer> qualityDistribution = new HashMap<>();
        Map<String, Integer> strategyDistribution = new HashMap<>();
        List<QualityTestResult> highQualityLogs = new ArrayList<>();
        List<QualityTestResult> lowQualityLogs = new ArrayList<>();

        int totalLogs = 0;
        int errorLogs = 0;
        int logsWithStackTrace = 0;

        // When: 모든 로그 파일 분석
        System.out.println("\n=== 실제 게임 로그 Fingerprint Quality 분석 ===\n");

        for (File logFile : logFiles) {
            JsonNode logsArray = objectMapper.readTree(logFile);

            for (JsonNode logEntry : logsArray) {
                totalLogs++;

                String severity = logEntry.path("severity").asText();
                String body = logEntry.path("body").asText();

                // ERROR/WARN 로그만 분석 (INFO는 제외)
                if (!"ERROR".equals(severity) && !"WARN".equals(severity)) {
                    continue;
                }

                errorLogs++;

                // GameLog 엔티티 생성
                GameLog gameLog = createGameLogFromJson(logEntry);
                FingerprintResult result = generator.generate(gameLog);

                // 통계 수집
                qualityDistribution.merge(result.getQuality(), 1, Integer::sum);
                strategyDistribution.merge(result.getStrategy(), 1, Integer::sum);

                // 스택트레이스 포함 여부 확인
                if (body.contains("at ")) {
                    logsWithStackTrace++;
                }

                // 샘플 수집
                QualityTestResult testResult =
                        new QualityTestResult(
                                logFile.getName(),
                                severity,
                                body.substring(0, Math.min(100, body.length())),
                                result);

                if (result.getQuality() == FingerprintQuality.HIGH) {
                    if (highQualityLogs.size() < 10) {
                        highQualityLogs.add(testResult);
                    }
                } else if (result.getQuality() == FingerprintQuality.FALLBACK
                        || result.getQuality() == FingerprintQuality.VERY_LOW) {
                    if (lowQualityLogs.size() < 10) {
                        lowQualityLogs.add(testResult);
                    }
                }
            }
        }

        // Then: 결과 출력 및 검증
        final int finalErrorLogs = errorLogs; // 람다식에서 사용하기 위해 final 변수로 저장

        System.out.println("📊 전체 통계");
        System.out.println("─────────────────────────────────────");
        System.out.printf("총 로그 수: %d%n", totalLogs);
        System.out.printf(
                "ERROR/WARN 로그 수: %d (%.1f%%)%n",
                finalErrorLogs, finalErrorLogs * 100.0 / totalLogs);
        System.out.printf(
                "스택트레이스 포함: %d (%.1f%%)%n",
                logsWithStackTrace, logsWithStackTrace * 100.0 / finalErrorLogs);

        System.out.println("\n📈 Quality 분포");
        System.out.println("─────────────────────────────────────");
        qualityDistribution.entrySet().stream()
                .sorted(Map.Entry.<FingerprintQuality, Integer>comparingByValue().reversed())
                .forEach(
                        entry ->
                                System.out.printf(
                                        "%s: %d (%.1f%%)%n",
                                        entry.getKey(),
                                        entry.getValue(),
                                        entry.getValue() * 100.0 / finalErrorLogs));

        System.out.println("\n🔧 Strategy 분포");
        System.out.println("─────────────────────────────────────");
        strategyDistribution.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(
                        entry ->
                                System.out.printf(
                                        "%s: %d (%.1f%%)%n",
                                        entry.getKey(),
                                        entry.getValue(),
                                        entry.getValue() * 100.0 / finalErrorLogs));

        System.out.println("\n✅ HIGH Quality 샘플 (최대 10개)");
        System.out.println("─────────────────────────────────────");
        highQualityLogs.forEach(
                r ->
                        System.out.printf(
                                "[%s] %s | %s | Body: %s...%n",
                                r.fileName, r.severity, r.result.getStrategy(), r.bodyPreview));

        System.out.println("\n⚠️ LOW/FALLBACK Quality 샘플 (최대 10개)");
        System.out.println("─────────────────────────────────────");
        lowQualityLogs.forEach(
                r ->
                        System.out.printf(
                                "[%s] %s | %s | Body: %s...%n",
                                r.fileName, r.severity, r.result.getStrategy(), r.bodyPreview));

        // 검증: HIGH quality가 MEDIUM보다 많아야 함 (실제 로그 사용 시)
        int highCount = qualityDistribution.getOrDefault(FingerprintQuality.HIGH, 0);
        int mediumCount = qualityDistribution.getOrDefault(FingerprintQuality.MEDIUM, 0);

        System.out.println("\n🎯 목표 달성 여부");
        System.out.println("─────────────────────────────────────");
        System.out.printf("HIGH quality: %d%n", highCount);
        System.out.printf("MEDIUM quality: %d%n", mediumCount);

        if (highCount > mediumCount) {
            System.out.println("✅ 성공: HIGH quality가 MEDIUM보다 많습니다!");
        } else {
            System.out.println("⚠️ 주의: HIGH quality가 MEDIUM 이하입니다. 스택트레이스 파싱 로직을 확인하세요.");
        }

        // 최소 검증: ERROR 로그가 있어야 함
        assertThat(finalErrorLogs).as("ERROR/WARN 로그가 존재해야 합니다").isGreaterThan(0);
    }

    @Test
    @DisplayName("특정 로그 파일의 상세 분석")
    void analyzeSpecificLogFile() throws IOException {
        // Given: 특정 로그 파일 선택
        File logFile =
                new ClassPathResource(
                                "fixtures/logprocessor/game-logs/2026-02-19T17-38-04_log.json")
                        .getFile();
        JsonNode logsArray = objectMapper.readTree(logFile);

        System.out.println("\n=== 개별 로그 상세 분석 ===");
        System.out.printf("파일: %s%n%n", logFile.getName());

        int index = 0;
        for (JsonNode logEntry : logsArray) {
            index++;
            String severity = logEntry.path("severity").asText();

            if (!"ERROR".equals(severity) && !"WARN".equals(severity)) {
                continue;
            }

            GameLog gameLog = createGameLogFromJson(logEntry);
            FingerprintResult result = generator.generate(gameLog);
            String body = logEntry.path("body").asText();

            System.out.printf("─────── Entry #%d ───────%n", index);
            System.out.printf("Severity: %s%n", severity);
            System.out.printf("Quality: %s%n", result.getQuality());
            System.out.printf("Strategy: %s%n", result.getStrategy());
            System.out.printf("Fingerprint: %s%n", result.getFingerprint());
            System.out.printf(
                    "Body Preview: %s%n%n", body.substring(0, Math.min(150, body.length())));
        }
    }

    @Test
    @DisplayName("스택트레이스가 있는 로그만 필터링하여 분석")
    void analyzeOnlyLogsWithStackTrace() throws IOException {
        File logDirectory = new ClassPathResource("fixtures/logprocessor/game-logs").getFile();
        File[] logFiles = logDirectory.listFiles((dir, name) -> name.endsWith(".json"));

        List<QualityTestResult> stackTraceLogs = new ArrayList<>();

        for (File logFile : logFiles) {
            JsonNode logsArray = objectMapper.readTree(logFile);

            for (JsonNode logEntry : logsArray) {
                String body = logEntry.path("body").asText();
                String severity = logEntry.path("severity").asText();

                // 스택트레이스가 있는 로그만
                if (body.contains("at ") && ("ERROR".equals(severity) || "WARN".equals(severity))) {
                    GameLog gameLog = createGameLogFromJson(logEntry);
                    FingerprintResult result = generator.generate(gameLog);

                    stackTraceLogs.add(
                            new QualityTestResult(
                                    logFile.getName(),
                                    severity,
                                    body.substring(0, Math.min(200, body.length())),
                                    result));
                }
            }
        }

        System.out.println("\n=== 스택트레이스 포함 로그 분석 ===");
        System.out.printf("총 개수: %d%n%n", stackTraceLogs.size());

        Map<FingerprintQuality, Long> qualityCount =
                stackTraceLogs.stream()
                        .collect(
                                Collectors.groupingBy(
                                        r -> r.result.getQuality(), Collectors.counting()));

        System.out.println("Quality 분포:");
        qualityCount.forEach(
                (quality, count) ->
                        System.out.printf(
                                "%s: %d (%.1f%%)%n",
                                quality, count, count * 100.0 / stackTraceLogs.size()));

        // 스택트레이스가 있으면 최소 MEDIUM 이상이어야 함
        long lowOrFallbackCount =
                qualityCount.getOrDefault(FingerprintQuality.VERY_LOW, 0L)
                        + qualityCount.getOrDefault(FingerprintQuality.FALLBACK, 0L);

        System.out.println("\n검증 결과:");
        if (lowOrFallbackCount == 0) {
            System.out.println("✅ 모든 스택트레이스 로그가 MEDIUM 이상의 quality를 가집니다!");
        } else {
            System.out.printf(
                    "⚠️ 주의: %d개의 로그가 VERY_LOW/FALLBACK quality입니다.%n", lowOrFallbackCount);
        }
    }

    private GameLog createGameLogFromJson(JsonNode logEntry) {
        String projectIdStr = logEntry.path("project_id").asText();
        UUID projectId;

        try {
            projectId = UUID.fromString(projectIdStr);
        } catch (IllegalArgumentException e) {
            // project_id가 문자열인 경우 (예: "PROJECT_LOL_TEST")
            // 문자열을 고정된 UUID로 변환
            projectId = UUID.nameUUIDFromBytes(projectIdStr.getBytes());
        }

        return GameLog.builder()
                .logId(UUID.fromString(logEntry.path("id").asText()))
                .projectId(projectId)
                .sessionId(logEntry.path("session_id").asText())
                .userId(logEntry.path("user_id").asText(null))
                .severity(LogSeverity.valueOf(logEntry.path("severity").asText()))
                .eventCategory(EventCategory.valueOf(logEntry.path("event_category").asText()))
                .archive(logEntry.path("body").asText()) // body → archive
                .occurredAt(OffsetDateTime.parse(logEntry.path("occurred_at").asText()))
                .ingestedAt(OffsetDateTime.parse(logEntry.path("ingested_at").asText()))
                .fingerprint(logEntry.path("fingerprint").asText(null))
                .resource(objectMapper.convertValue(logEntry.path("resource"), Map.class))
                .attributes(objectMapper.convertValue(logEntry.path("attributes"), Map.class))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private static class QualityTestResult {
        String fileName;
        String severity;
        String bodyPreview;
        FingerprintResult result;

        QualityTestResult(
                String fileName, String severity, String bodyPreview, FingerprintResult result) {
            this.fileName = fileName;
            this.severity = severity;
            this.bodyPreview = bodyPreview;
            this.result = result;
        }
    }
}

package kr.java.documind.domain.issue.service.fingerprint;

import static kr.java.documind.domain.issue.service.fingerprint.GameErrorPatterns.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.enums.EventCategory;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 실제 생성되는 quality 확인용 디버그 테스트 */
class DebugFingerprintTest {

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
    void debugAllPatterns() {
        System.out.println("=== Java 표준 예외 ===");
        printResult("NPE_STANDARD", NPE_STANDARD);
        printResult("INDEX_OUT_OF_BOUNDS", INDEX_OUT_OF_BOUNDS);
        printResult("CLASS_CAST", CLASS_CAST);
        printResult("OUT_OF_MEMORY", OUT_OF_MEMORY);

        System.out.println("\n=== Unity 에러 ===");
        printResult("UNITY_NULL_REFERENCE", UNITY_NULL_REFERENCE);

        System.out.println("\n=== 네트워크 에러 ===");
        printResult("NETWORK_CONNECT_TIMEOUT", NETWORK_CONNECT_TIMEOUT);
        printResult("NETWORK_SOCKET_TIMEOUT", NETWORK_SOCKET_TIMEOUT);
        printResult("NETWORK_UNKNOWN_HOST", NETWORK_UNKNOWN_HOST);

        System.out.println("\n=== 데이터베이스 에러 ===");
        printResult("DB_DEADLOCK", DB_DEADLOCK);
        printResult("DB_QUERY_TIMEOUT", DB_QUERY_TIMEOUT);

        System.out.println("\n=== 멀티스레딩 에러 ===");
        printResult("CONCURRENT_MODIFICATION", CONCURRENT_MODIFICATION);
        printResult("ILLEGAL_MONITOR_STATE", ILLEGAL_MONITOR_STATE);

        System.out.println("\n=== JSON/Serialization 에러 ===");
        printResult("JSON_MAPPING_EXCEPTION", JSON_MAPPING_EXCEPTION);
        printResult("GSON_SYNTAX_EXCEPTION", GSON_SYNTAX_EXCEPTION);
    }

    private void printResult(String name, String archive) {
        GameLog log = createGameLog(archive);
        FingerprintResult result = generator.generate(log);
        System.out.printf(
                "%s: quality=%s, strategy=%s%n", name, result.getQuality(), result.getStrategy());
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

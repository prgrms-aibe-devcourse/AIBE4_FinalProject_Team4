package kr.java.documind.domain.issue.service.fingerprint;

import static kr.java.documind.domain.issue.service.fingerprint.GameErrorPatterns.*;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FingerprintGenerator - 게임 에러 패턴 확장 테스트")
class FingerprintGeneratorAdvancedTest {

    private FingerprintGenerator generator;
    private MessageNormalizer messageNormalizer;
    private StackFrameFilter stackFrameFilter;

    @BeforeEach
    void setUp() {
        messageNormalizer = new MessageNormalizer();
        stackFrameFilter = new StackFrameFilter();
        generator = new FingerprintGenerator(messageNormalizer, stackFrameFilter);
    }

    @Nested
    @DisplayName("Java 표준 예외 패턴")
    class JavaStandardExceptionTests {

        @Test
        @DisplayName("NullPointerException - 가장 흔한 에러")
        void nullPointerException() {
            GameLog log = createGameLog(NPE_STANDARD);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
            assertThat(result.getFingerprint()).hasSize(64);
            assertThat(result.requiresReview()).isFalse();
        }

        @Test
        @DisplayName("IndexOutOfBoundsException")
        void indexOutOfBoundsException() {
            GameLog log = createGameLog(INDEX_OUT_OF_BOUNDS);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
            assertThat(result.getFingerprint()).hasSize(64);
        }

        @Test
        @DisplayName("ClassCastException")
        void classCastException() {
            GameLog log = createGameLog(CLASS_CAST);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.MEDIUM);
            assertThat(result.getFingerprint()).hasSize(64);
        }

        @Test
        @DisplayName("IllegalStateException")
        void illegalStateException() {
            GameLog log = createGameLog(ILLEGAL_STATE);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.MEDIUM);
        }

        @Test
        @DisplayName("OutOfMemoryError - 치명적 에러")
        void outOfMemoryError() {
            GameLog log = createGameLog(OUT_OF_MEMORY);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
            assertThat(result.getStrategy()).contains("Stacktrace");
        }

        @Test
        @DisplayName("StackOverflowError - 재귀 호출")
        void stackOverflowError() {
            GameLog log = createGameLog(STACK_OVERFLOW);

            FingerprintResult result = generator.generate(log);

            // 동일한 프레임 반복이지만 정규화되어 동일한 fingerprint
            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }
    }

    @Nested
    @DisplayName("Unity 게임 엔진 에러 패턴")
    class UnityEngineTests {

        @Test
        @DisplayName("Unity NullReferenceException")
        void unityNullReference() {
            GameLog log = createGameLog(UNITY_NULL_REFERENCE);

            FingerprintResult result = generator.generate(log);

            // Unity 스택트레이스도 파싱 가능해야 함
            assertThat(result.getQuality())
                    .isIn(FingerprintQuality.HIGH, FingerprintQuality.MEDIUM);
            assertThat(result.getFingerprint()).hasSize(64);
        }

        @Test
        @DisplayName("Unity UnassignedReferenceException")
        void unityUnassignedReference() {
            GameLog log = createGameLog(UNITY_UNASSIGNED_REFERENCE);

            FingerprintResult result = generator.generate(log);

            // 긴 에러 메시지도 정규화되어야 함
            assertThat(result.getFingerprint()).hasSize(64);
        }

        @Test
        @DisplayName("Unity MissingComponentException")
        void unityMissingComponent() {
            GameLog log = createGameLog(UNITY_MISSING_COMPONENT);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getFingerprint()).hasSize(64);
        }
    }

    @Nested
    @DisplayName("Unreal Engine 에러 패턴")
    class UnrealEngineTests {

        @Test
        @DisplayName("Unreal Assertion Failed")
        void unrealAssertionFailed() {
            GameLog log = createGameLog(UNREAL_ASSERTION_FAILED);

            FingerprintResult result = generator.generate(log);

            // Unreal 로그 포맷도 처리 가능해야 함
            assertThat(result.getFingerprint()).hasSize(64);
        }

        @Test
        @DisplayName("Unreal Blueprint Error")
        void unrealBlueprintError() {
            GameLog log = createGameLog(UNREAL_BLUEPRINT_ERROR);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getFingerprint()).hasSize(64);
        }
    }

    @Nested
    @DisplayName("네트워크 에러 패턴")
    class NetworkErrorTests {

        @Test
        @DisplayName("Connection Timeout")
        void connectionTimeout() {
            GameLog log = createGameLog(NETWORK_CONNECT_TIMEOUT);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }

        @Test
        @DisplayName("Socket Timeout")
        void socketTimeout() {
            GameLog log = createGameLog(NETWORK_SOCKET_TIMEOUT);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }

        @Test
        @DisplayName("UnknownHostException - DNS 문제")
        void unknownHost() {
            GameLog log = createGameLog(NETWORK_UNKNOWN_HOST);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }

        @Test
        @DisplayName("Netty Connection Timeout")
        void nettyTimeout() {
            GameLog log = createGameLog(NETWORK_NETTY_TIMEOUT);

            FingerprintResult result = generator.generate(log);

            // Netty 프레임도 필터링되어야 함
            assertThat(result.getFingerprint()).hasSize(64);
        }
    }

    @Nested
    @DisplayName("데이터베이스 에러 패턴")
    class DatabaseErrorTests {

        @Test
        @DisplayName("Deadlock - 트랜잭션 충돌")
        void deadlock() {
            GameLog log = createGameLog(DB_DEADLOCK);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }

        @Test
        @DisplayName("Duplicate Key - Unique 제약 위반")
        void duplicateKey() {
            GameLog log = createGameLog(DB_DUPLICATE_KEY);

            FingerprintResult result = generator.generate(log);

            // PostgreSQL 에러도 처리 가능해야 함
            assertThat(result.getFingerprint()).hasSize(64);
        }

        @Test
        @DisplayName("Query Timeout")
        void queryTimeout() {
            GameLog log = createGameLog(DB_QUERY_TIMEOUT);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }
    }

    @Nested
    @DisplayName("게임 비즈니스 로직 에러 패턴")
    class GameBusinessLogicTests {

        @Test
        @DisplayName("Inventory Full - 인벤토리 가득 참")
        void inventoryFull() {
            GameLog log = createGameLog(GAME_INVENTORY_FULL);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
            assertThat(result.requiresReview()).isFalse();
        }

        @Test
        @DisplayName("Insufficient Currency - 재화 부족")
        void insufficientCurrency() {
            GameLog log = createGameLog(GAME_INSUFFICIENT_CURRENCY);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }

        @Test
        @DisplayName("Matchmaking Timeout - 매칭 시간 초과")
        void matchmakingTimeout() {
            GameLog log = createGameLog(GAME_MATCHMAKING_TIMEOUT);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }

        @Test
        @DisplayName("Player Banned - 계정 정지")
        void playerBanned() {
            GameLog log = createGameLog(GAME_PLAYER_BANNED);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }

        @Test
        @DisplayName("Invalid Game State - 잘못된 게임 상태")
        void invalidGameState() {
            GameLog log = createGameLog(GAME_INVALID_STATE);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }
    }

    @Nested
    @DisplayName("동적 데이터 정규화 테스트")
    class DynamicDataNormalizationTests {

        @Test
        @DisplayName("UUID 정규화 - 동일한 에러로 그룹핑")
        void uuidNormalization() {
            GameLog log1 = createGameLog(DYNAMIC_UUID);
            GameLog log2 =
                    createGameLog(
                            DYNAMIC_UUID.replace(
                                    "550e8400-e29b-41d4-a716-446655440000",
                                    "123e4567-e89b-12d3-a456-426614174000"));

            FingerprintResult result1 = generator.generate(log1);
            FingerprintResult result2 = generator.generate(log2);

            // UUID가 다르지만 정규화되어 동일한 fingerprint
            assertThat(result1.getFingerprint()).isEqualTo(result2.getFingerprint());
        }

        @Test
        @DisplayName("IP 주소 정규화")
        void ipAddressNormalization() {
            GameLog log1 = createGameLog(DYNAMIC_IP_ADDRESS);
            GameLog log2 =
                    createGameLog(
                            DYNAMIC_IP_ADDRESS
                                    .replace("192.168.1.100", "10.0.0.5")
                                    .replace("10.0.0.5:54321", "192.168.1.1:12345"));

            FingerprintResult result1 = generator.generate(log1);
            FingerprintResult result2 = generator.generate(log2);

            // IP 주소가 다르지만 정규화되어 동일한 fingerprint
            assertThat(result1.getFingerprint()).isEqualTo(result2.getFingerprint());
        }

        @Test
        @DisplayName("타임스탬프 정규화")
        void timestampNormalization() {
            GameLog log1 = createGameLog(DYNAMIC_TIMESTAMP);
            GameLog log2 =
                    createGameLog(
                            DYNAMIC_TIMESTAMP.replace(
                                    "2024-02-04T14:30:25.123Z", "2024-03-05T09:15:30.456Z"));

            FingerprintResult result1 = generator.generate(log1);
            FingerprintResult result2 = generator.generate(log2);

            // 타임스탬프가 다르지만 정규화되어 동일한 fingerprint
            assertThat(result1.getFingerprint()).isEqualTo(result2.getFingerprint());
        }

        @Test
        @DisplayName("Windows 파일 경로 정규화")
        void windowsFilePathNormalization() {
            GameLog log1 = createGameLog(DYNAMIC_FILE_PATH_WINDOWS);
            GameLog log2 =
                    createGameLog(
                            DYNAMIC_FILE_PATH_WINDOWS.replace(
                                    "C:\\Users\\Player\\Documents\\GameData\\config.json",
                                    "C:\\Users\\AnotherUser\\Documents\\GameData\\config.json"));

            FingerprintResult result1 = generator.generate(log1);
            FingerprintResult result2 = generator.generate(log2);

            // 파일 경로가 다르지만 파일명은 같아서 동일한 fingerprint
            assertThat(result1.getFingerprint()).isEqualTo(result2.getFingerprint());
        }

        @Test
        @DisplayName("Unix 파일 경로 정규화")
        void unixFilePathNormalization() {
            GameLog log1 = createGameLog(DYNAMIC_FILE_PATH_UNIX);
            GameLog log2 =
                    createGameLog(
                            DYNAMIC_FILE_PATH_UNIX.replace(
                                    "player_data_12345.sav", "player_data_67890.sav"));

            FingerprintResult result1 = generator.generate(log1);
            FingerprintResult result2 = generator.generate(log2);

            // 플레이어 ID가 다르지만 정규화되어 동일한 fingerprint
            assertThat(result1.getFingerprint()).isEqualTo(result2.getFingerprint());
        }

        @Test
        @DisplayName("아이템 ID 정규화")
        void itemIdNormalization() {
            GameLog log1 = createGameLog(DYNAMIC_ITEM_IDS);
            GameLog log2 =
                    createGameLog(
                            DYNAMIC_ITEM_IDS
                                    .replace(
                                            "item_weapon_legendary_001",
                                            "item_weapon_legendary_999")
                                    .replace("item_armor_helmet_005", "item_armor_helmet_123"));

            FingerprintResult result1 = generator.generate(log1);
            FingerprintResult result2 = generator.generate(log2);

            // 아이템 ID 숫자가 다르지만 타입은 같아서 동일한 fingerprint
            assertThat(result1.getFingerprint()).isEqualTo(result2.getFingerprint());
        }
    }

    @Nested
    @DisplayName("멀티스레딩 에러 패턴")
    class MultiThreadingTests {

        @Test
        @DisplayName("ConcurrentModificationException")
        void concurrentModification() {
            GameLog log = createGameLog(CONCURRENT_MODIFICATION);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }

        @Test
        @DisplayName("IllegalMonitorStateException")
        void illegalMonitorState() {
            GameLog log = createGameLog(ILLEGAL_MONITOR_STATE);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }
    }

    @Nested
    @DisplayName("JSON/Serialization 에러 패턴")
    class SerializationTests {

        @Test
        @DisplayName("Jackson JsonMappingException")
        void jacksonMappingException() {
            GameLog log = createGameLog(JSON_MAPPING_EXCEPTION);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }

        @Test
        @DisplayName("Gson JsonSyntaxException")
        void gsonSyntaxException() {
            GameLog log = createGameLog(GSON_SYNTAX_EXCEPTION);

            FingerprintResult result = generator.generate(log);

            assertThat(result.getQuality()).isEqualTo(FingerprintQuality.HIGH);
        }
    }

    @Nested
    @DisplayName("라인 번호 정규화 테스트")
    class LineNumberNormalizationTests {

        @Test
        @DisplayName("라인 번호 차이 (42, 44, 45) → 동일한 fingerprint")
        void lineNumberNormalization() {
            String base =
                    """
                    java.lang.NullPointerException: Test error
                    at com.game.service.TestService.method(TestService.java:42)
                    at com.game.controller.TestController.handle(TestController.java:15)
                    """;

            String variant1 = base.replace(":42)", ":44)");
            String variant2 = base.replace(":42)", ":45)");

            GameLog log1 = createGameLog(base);
            GameLog log2 = createGameLog(variant1);
            GameLog log3 = createGameLog(variant2);

            FingerprintResult result1 = generator.generate(log1);
            FingerprintResult result2 = generator.generate(log2);
            FingerprintResult result3 = generator.generate(log3);

            // 라인 번호가 10 단위로 정규화되어 동일한 fingerprint (42, 44, 45 → 40)
            assertThat(result1.getFingerprint()).isEqualTo(result2.getFingerprint());
            assertThat(result2.getFingerprint()).isEqualTo(result3.getFingerprint());
        }

        @Test
        @DisplayName("라인 번호 큰 차이 (42, 152) → 다른 fingerprint")
        void lineNumberDifferentBlocks() {
            String base =
                    """
                    java.lang.NullPointerException: Test error
                    at com.game.service.TestService.method(TestService.java:42)
                    """;

            String variant = base.replace(":42)", ":152)");

            GameLog log1 = createGameLog(base);
            GameLog log2 = createGameLog(variant);

            FingerprintResult result1 = generator.generate(log1);
            FingerprintResult result2 = generator.generate(log2);

            // 라인 번호가 다른 블록 (42 → 40, 152 → 150)
            assertThat(result1.getFingerprint()).isNotEqualTo(result2.getFingerprint());
        }
    }

    // 헬퍼 메서드
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

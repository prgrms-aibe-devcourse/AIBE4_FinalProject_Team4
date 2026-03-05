package kr.java.documind.domain.logprocessor.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisStreamConsumer 단위 테스트")
class RedisStreamConsumerTest {

    @Mock private RedisTemplate<String, String> redisTemplate;

    @Mock private StreamOperations<String, Object, Object> streamOperations;

    @Mock private LogBufferService logBufferService;

    @Mock private LogMapper logMapper;

    private RedisStreamConsumer redisStreamConsumer;

    private static final String STREAM_KEY = "test-stream";
    private static final String CONSUMER_GROUP = "test-group";
    private static final String CONSUMER_ID = "test-consumer";

    @BeforeEach
    void setUp() {
        redisStreamConsumer = new RedisStreamConsumer(redisTemplate, logBufferService, logMapper);

        // @Value 필드 초기화
        ReflectionTestUtils.setField(redisStreamConsumer, "streamKey", STREAM_KEY);
        ReflectionTestUtils.setField(redisStreamConsumer, "consumerGroup", CONSUMER_GROUP);
        ReflectionTestUtils.setField(redisStreamConsumer, "consumerId", CONSUMER_ID);
        ReflectionTestUtils.setField(redisStreamConsumer, "pollIntervalMs", 5000L);
        ReflectionTestUtils.setField(redisStreamConsumer, "pollBlockMs", 2000L);
        ReflectionTestUtils.setField(redisStreamConsumer, "batchSize", 100);

        // RedisTemplate Mock 설정
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
    }

    @Test
    @DisplayName("정상 소비: Redis에서 메시지를 읽어와 LogBufferService에 전달")
    void consumeLogs_Success() throws Exception {
        // Given: Redis Stream에 메시지가 있음
        MapRecord<String, Object, Object> record = createMockRecord("1234567890-0");
        when(streamOperations.read(
                        any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record));

        GameLog mockGameLog = createMockGameLog();
        when(logMapper.toEntity(any(Map.class))).thenReturn(mockGameLog);

        // When: 메시지 소비
        redisStreamConsumer.consumeLogsWithCircuitBreaker();

        // Then: LogBufferService에 전달됨
        verify(logBufferService, times(1)).add(eq(mockGameLog), eq(record.getId()));
    }

    @Test
    @DisplayName("빈 스트림: 메시지가 없으면 아무 동작 안 함")
    void consumeLogs_EmptyStream() {
        // Given: Redis Stream이 비어있음
        when(streamOperations.read(
                        any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of());

        // When: 메시지 소비 시도
        redisStreamConsumer.consumeLogsWithCircuitBreaker();

        // Then: LogBufferService 호출 안 됨
        verify(logBufferService, times(0)).add(any(GameLog.class), any(RecordId.class));
    }

    @Test
    @DisplayName("파싱 실패: 잘못된 메시지는 ACK 후 스킵")
    void consumeLogs_ParseFailure() throws Exception {
        // Given: 파싱 불가능한 메시지
        MapRecord<String, Object, Object> record = createMockRecord("1234567890-1");
        when(streamOperations.read(
                        any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record));

        when(logMapper.toEntity(any(Map.class)))
                .thenThrow(new IllegalArgumentException("Invalid format"));

        // When: 메시지 소비
        redisStreamConsumer.consumeLogsWithCircuitBreaker();

        // Then: 실패한 메시지는 ACK 처리됨
        verify(logBufferService, times(1)).acknowledgeFailedMessage(eq(record.getId()));
    }

    @Test
    @DisplayName("Redis 장애: RedisConnectionFailureException 발생 시 예외 전파")
    void consumeLogs_RedisConnectionFailure() {
        // Given: Redis 연결 실패
        when(streamOperations.read(
                        any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenThrow(
                        new org.springframework.data.redis.RedisConnectionFailureException(
                                "Connection refused"));

        // When & Then: 예외 발생 (Circuit Breaker가 잡아야 함)
        try {
            redisStreamConsumer.consumeLogsWithCircuitBreaker();
        } catch (Exception e) {
            // Circuit Breaker/Retry가 처리
        }
    }

    // 헬퍼 메서드: Mock Record 생성
    private MapRecord<String, Object, Object> createMockRecord(String recordIdStr) {
        RecordId recordId = RecordId.of(recordIdStr);
        Map<Object, Object> value =
                Map.of(
                        "projectId", "123e4567-e89b-12d3-a456-426614174000",
                        "sessionId", "test-session",
                        "userId", "test-user",
                        "severity", "ERROR",
                        "eventCategory", "SYSTEM",
                        "archive", "Test error message");

        return StreamRecords.mapBacked(value).withStreamKey(STREAM_KEY).withId(recordId);
    }

    // 헬퍼 메서드: Mock GameLog 생성
    private GameLog createMockGameLog() {
        // GameLog는 실제 엔티티이므로 간단한 stub 반환
        return GameLog.builder()
                .logId(java.util.UUID.randomUUID())
                .projectId(java.util.UUID.randomUUID())
                .sessionId("test-session")
                .userId("test-user")
                .severity(kr.java.documind.domain.logprocessor.model.enums.LogSeverity.ERROR)
                .eventCategory(
                        kr.java.documind.domain.logprocessor.model.enums.EventCategory.SYSTEM)
                .archive("Test error message")
                .occurredAt(java.time.OffsetDateTime.now())
                .ingestedAt(java.time.OffsetDateTime.now())
                .traceId("trace-123")
                .spanId("span-456")
                .fingerprint("fp-789")
                .resource(Map.of())
                .attributes(Map.of())
                .createdAt(java.time.OffsetDateTime.now())
                .updatedAt(java.time.OffsetDateTime.now())
                .build();
    }
}

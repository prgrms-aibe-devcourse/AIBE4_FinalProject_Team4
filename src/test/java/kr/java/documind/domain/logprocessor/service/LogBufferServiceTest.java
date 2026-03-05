package kr.java.documind.domain.logprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.logprocessor.model.dto.request.RawLogRequest;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.enums.EventCategory;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import kr.java.documind.domain.logprocessor.model.repository.LogJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogBufferService 단위 테스트")
class LogBufferServiceTest {

    @Mock private LogJdbcRepository logJdbcRepository;

    @Mock private RedisTemplate<String, String> redisTemplate;

    @Mock private StreamOperations<String, Object, Object> streamOperations;

    @Mock private BackpressureManager backpressureManager;

    @Mock private LogMapper logMapper;

    private MeterRegistry meterRegistry;
    private LogBufferService logBufferService;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_BUFFER_SIZE = 10000;
    private static final int MAX_RETRY_COUNT = 3;
    private static final String STREAM_KEY = "test-stream";
    private static final String CONSUMER_GROUP = "test-group";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        logBufferService =
                new LogBufferService(
                        logJdbcRepository,
                        redisTemplate,
                        backpressureManager,
                        meterRegistry,
                        logMapper);

        // @Value 필드 초기화
        ReflectionTestUtils.setField(logBufferService, "batchSize", BATCH_SIZE);
        ReflectionTestUtils.setField(logBufferService, "maxBufferSize", MAX_BUFFER_SIZE);
        ReflectionTestUtils.setField(logBufferService, "maxRetryCount", MAX_RETRY_COUNT);
        ReflectionTestUtils.setField(logBufferService, "streamKey", STREAM_KEY);
        ReflectionTestUtils.setField(logBufferService, "consumerGroup", CONSUMER_GROUP);

        // RedisTemplate Mock 설정 (lenient)
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        // BackpressureManager Mock 설정 (lenient)
        lenient().when(backpressureManager.getCurrentBatchSize()).thenReturn(BATCH_SIZE);

        // @PostConstruct 메서드 수동 호출
        logBufferService.init();
    }

    // 헬퍼 메서드: GameLog 생성
    private GameLog createGameLog(String logIdSuffix) {
        OffsetDateTime now = OffsetDateTime.now();
        return GameLog.builder()
                .logId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .sessionId("test-session")
                .userId("test-user")
                .severity(LogSeverity.INFO)
                .eventCategory(EventCategory.SYSTEM)
                .archive("Test log message: " + logIdSuffix)
                .occurredAt(now)
                .ingestedAt(now)
                .traceId("trace-123")
                .spanId("span-456")
                .fingerprint("fp-789")
                .resource(Map.of())
                .attributes(Map.of())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // 헬퍼 메서드: RawLogRequest 생성
    private RawLogRequest createRawLogRequest(String archiveMessage) {
        return new RawLogRequest(
                UUID.randomUUID(),
                "test-session",
                "test-user",
                LogSeverity.INFO,
                EventCategory.SYSTEM,
                archiveMessage,
                OffsetDateTime.now().toString(),
                "trace-123",
                "span-456",
                Map.of(),
                Map.of());
    }

    @Test
    @DisplayName("버퍼 추가: 로그를 버퍼에 성공적으로 추가")
    void addToBuffer_Success() throws Exception {
        // Given
        GameLog log = createGameLog("log-1");
        RecordId recordId = RecordId.of("1234567890-0");

        int initialBufferSize = getBufferSize();

        // When
        logBufferService.add(log, recordId);

        // Then: 버퍼 크기 증가
        int finalBufferSize = getBufferSize();
        assertThat(finalBufferSize).isEqualTo(initialBufferSize + 1);
    }

    @Test
    @DisplayName("버퍼 추가: RecordId 없이 추가 가능 (API 요청용)")
    void addToBuffer_WithoutRecordId() throws Exception {
        // Given
        GameLog log = createGameLog("log-2");

        int initialBufferSize = getBufferSize();

        // When
        logBufferService.add(log);

        // Then: 버퍼 크기 증가
        int finalBufferSize = getBufferSize();
        assertThat(finalBufferSize).isEqualTo(initialBufferSize + 1);
    }

    @Test
    @DisplayName("버퍼 오버플로우: 최대 크기 초과 시 로그 드롭")
    void addToBuffer_Overflow() throws Exception {
        // Given: 버퍼를 최대 크기로 설정 (테스트 속도를 위해 작은 값 사용)
        ReflectionTestUtils.setField(logBufferService, "maxBufferSize", 10);

        // And: 버퍼를 최대 크기로 채움
        for (int i = 0; i < 10; i++) {
            GameLog log = createGameLog("log-" + i);
            logBufferService.add(log);
        }

        int bufferSizeBeforeOverflow = getBufferSize();
        assertThat(bufferSizeBeforeOverflow).isEqualTo(10);

        // When: 추가 로그 삽입 시도
        GameLog extraLog = createGameLog("log-overflow");
        logBufferService.add(extraLog);

        // Then: 버퍼 크기 증가하지 않음 (드롭됨)
        int bufferSizeAfterOverflow = getBufferSize();
        assertThat(bufferSizeAfterOverflow).isEqualTo(bufferSizeBeforeOverflow);
    }

    @Test
    @DisplayName("DTO 변환: RawLogRequest를 GameLog로 변환 후 추가")
    void addFromDto_Success() throws Exception {
        // Given
        RawLogRequest dto = createRawLogRequest("log-3");
        GameLog expectedLog = createGameLog("log-3");

        when(logMapper.toEntity(dto)).thenReturn(expectedLog);

        int initialBufferSize = getBufferSize();

        // When
        logBufferService.addFromDto(dto);

        // Then: 버퍼 크기 증가
        int finalBufferSize = getBufferSize();
        assertThat(finalBufferSize).isEqualTo(initialBufferSize + 1);

        // And: LogMapper 호출 확인
        verify(logMapper, times(1)).toEntity(dto);
    }

    @Test
    @DisplayName("일괄 DTO 추가: 여러 DTO를 한 번에 추가")
    void addAllFromDtos_Success() throws Exception {
        // Given
        List<RawLogRequest> dtos =
                List.of(
                        createRawLogRequest("log-4"),
                        createRawLogRequest("log-5"),
                        createRawLogRequest("log-6"));

        when(logMapper.toEntity(any(RawLogRequest.class)))
                .thenAnswer(
                        invocation -> {
                            RawLogRequest dto = invocation.getArgument(0);
                            return createGameLog(dto.archive());
                        });

        int initialBufferSize = getBufferSize();

        // When
        logBufferService.addAllFromDtos(dtos);

        // Then: 버퍼 크기가 DTO 개수만큼 증가
        int finalBufferSize = getBufferSize();
        assertThat(finalBufferSize).isEqualTo(initialBufferSize + dtos.size());
    }

    @Test
    @DisplayName("Flush 성공: 버퍼의 로그를 DB에 저장하고 ACK 전송")
    void flush_Success() throws Exception {
        // Given: 버퍼에 로그 추가
        GameLog log1 = createGameLog("log-7");
        GameLog log2 = createGameLog("log-8");
        RecordId recordId1 = RecordId.of("1234567890-0");
        RecordId recordId2 = RecordId.of("1234567890-1");

        logBufferService.add(log1, recordId1);
        logBufferService.add(log2, recordId2);

        // When: Flush 실행
        logBufferService.flush();

        // Then: DB 저장 및 ACK 전송 확인
        verify(logJdbcRepository, times(1)).saveAll(anyList());
        verify(streamOperations, times(1))
                .acknowledge(eq(STREAM_KEY), eq(CONSUMER_GROUP), any(RecordId[].class));
        verify(backpressureManager, times(1)).recordLatency(anyLong());
    }

    @Test
    @DisplayName("Flush 실패: DB 저장 실패 시 DLQ로 이동")
    void flush_FailureMovesToDLQ() throws Exception {
        // Given: 버퍼에 로그 추가
        GameLog log = createGameLog("log-9");
        RecordId recordId = RecordId.of("1234567890-2");
        logBufferService.add(log, recordId);

        // And: DB 저장 실패 시뮬레이션
        doThrow(new RuntimeException("DB connection failed"))
                .when(logJdbcRepository)
                .saveAll(anyList());

        int initialDlqSize = getDlqSize();

        // When: Flush 실행
        logBufferService.flush();

        // Then: DLQ 크기 증가
        int finalDlqSize = getDlqSize();
        assertThat(finalDlqSize).isGreaterThan(initialDlqSize);
    }

    @Test
    @DisplayName("DLQ 재시도 성공: DLQ의 로그를 재처리하여 DB 저장")
    void retryDLQ_Success() throws Exception {
        // Given: DLQ에 로그 추가 (flush 실패로 인해)
        GameLog log = createGameLog("log-10");
        RecordId recordId = RecordId.of("1234567890-3");
        logBufferService.add(log, recordId);

        doThrow(new RuntimeException("First attempt failed"))
                .when(logJdbcRepository)
                .saveAll(anyList());
        logBufferService.flush();

        // DLQ에 추가되었는지 확인
        int dlqSizeAfterFailure = getDlqSize();
        assertThat(dlqSizeAfterFailure).isGreaterThan(0);

        // And: Mock을 리셋하고 DB 저장 성공하도록 변경
        reset(logJdbcRepository);
        doNothing().when(logJdbcRepository).saveAll(anyList());

        // When: DLQ 재시도
        logBufferService.retryDeadLetterQueue();

        // Then: DLQ 크기 감소 (재시도 성공 시 0이 됨)
        int dlqSizeAfterRetry = getDlqSize();
        assertThat(dlqSizeAfterRetry).isEqualTo(0);
    }

    @Test
    @DisplayName("DLQ 재시도 횟수 초과: 최대 재시도 후에도 실패 시 최종 실패 처리")
    void retryDLQ_MaxRetryExceeded() throws Exception {
        // Given: DLQ에 로그 추가
        GameLog log = createGameLog("log-11");
        RecordId recordId = RecordId.of("1234567890-4");
        logBufferService.add(log, recordId);

        // And: DB 저장 계속 실패하도록 설정
        doThrow(new RuntimeException("Persistent DB failure"))
                .when(logJdbcRepository)
                .saveAll(anyList());

        logBufferService.flush();

        int initialDlqSize = getDlqSize();

        // When: 최대 재시도 횟수만큼 재시도
        for (int i = 0; i < MAX_RETRY_COUNT; i++) {
            logBufferService.retryDeadLetterQueue();
        }

        // Then: DLQ가 비워짐 (최종 실패 처리)
        int finalDlqSize = getDlqSize();
        assertThat(finalDlqSize).isLessThan(initialDlqSize);
    }

    @Test
    @DisplayName("RecordId 없는 로그: ACK 전송하지 않음 (API 요청)")
    void flush_WithoutRecordId_NoAck() throws Exception {
        // Given: RecordId 없이 로그 추가 (API 요청)
        GameLog log = createGameLog("log-12");
        logBufferService.add(log); // RecordId 없음

        // When: Flush 실행
        logBufferService.flush();

        // Then: DB 저장은 되지만 ACK는 전송되지 않음
        verify(logJdbcRepository, times(1)).saveAll(anyList());
        verify(streamOperations, times(0))
                .acknowledge(anyString(), anyString(), any(RecordId[].class));
    }

    @Test
    @DisplayName("메트릭 등록: 버퍼 및 DLQ 크기 Gauge 등록")
    void metricsRegistration_GaugesRegistered() {
        // Given: LogBufferService 초기화 완료

        // When: 메트릭 조회
        Double bufferSizeGauge = meterRegistry.get("worker.buffer.size").gauge().value();
        Double dlqSizeGauge = meterRegistry.get("worker.dlq.size").gauge().value();

        // Then: Gauge가 정상적으로 등록됨
        assertThat(bufferSizeGauge).isNotNull();
        assertThat(dlqSizeGauge).isNotNull();

        // And: 초기 크기는 0
        assertThat(bufferSizeGauge).isEqualTo(0.0);
        assertThat(dlqSizeGauge).isEqualTo(0.0);
    }

    @Test
    @DisplayName("메트릭 업데이트: 로그 추가 시 버퍼 크기 메트릭 변경")
    void metricsUpdate_AfterAddingLogs() throws Exception {
        // Given: 초기 버퍼 크기
        Double initialBufferSize = meterRegistry.get("worker.buffer.size").gauge().value();

        // When: 로그 추가
        GameLog log = createGameLog("log-13");
        logBufferService.add(log);

        // Then: 버퍼 크기 메트릭 증가
        Double updatedBufferSize = meterRegistry.get("worker.buffer.size").gauge().value();
        assertThat(updatedBufferSize).isGreaterThan(initialBufferSize);
    }

    // Private 헬퍼 메서드: 버퍼 크기 조회
    private int getBufferSize() throws Exception {
        var buffer = ReflectionTestUtils.getField(logBufferService, "buffer");
        return ((java.util.concurrent.ConcurrentLinkedQueue<?>) buffer).size();
    }

    // Private 헬퍼 메서드: DLQ 크기 조회
    private int getDlqSize() throws Exception {
        var dlq = ReflectionTestUtils.getField(logBufferService, "deadLetterQueue");
        return ((java.util.concurrent.ConcurrentLinkedQueue<?>) dlq).size();
    }
}

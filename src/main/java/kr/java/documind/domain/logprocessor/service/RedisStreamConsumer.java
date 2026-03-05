package kr.java.documind.domain.logprocessor.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Redis Streams Consumer with Circuit Breaker & Exponential Backoff
 *
 * <p>Redis Streams에서 로그를 읽어와 LogBufferService에 전달하는 Consumer Worker
 *
 * <p>장애 격리 및 재시도 전략: - Circuit Breaker: Redis 장애 시 자동으로 회로 차단 - Exponential Backoff: 재연결
 * 시 지수 백오프로 부하 감소 - Health Check: Circuit Breaker 상태를 Spring Actuator에 노출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStreamConsumer {

    private static final String CIRCUIT_BREAKER_NAME = "redisStreamConsumer";

    private final RedisTemplate<String, String> redisTemplate;
    private final LogBufferService logBufferService;
    private final LogMapper logMapper;

    @Value("${redis.stream.key}")
    private String streamKey;

    @Value("${redis.stream.group}")
    private String consumerGroup;

    @Value("${redis.stream.consumer}")
    private String consumerId;

    @Value("${worker.poll-interval-ms}")
    private long pollIntervalMs;

    @Value("${worker.poll-block-ms}")
    private long pollBlockMs;

    @Value("${worker.bulk.size}")
    private int batchSize;

    /**
     * Redis Streams에서 로그를 폴링하는 Scheduled Job
     *
     * <p>Circuit Breaker가 OPEN 상태면 자동으로 스킵됨
     */
    @Scheduled(fixedDelayString = "${worker.poll-interval-ms}")
    public void pollLogs() {
        try {
            consumeLogsWithCircuitBreaker();
        } catch (Exception e) {
            // Circuit Breaker OPEN 상태이거나 예외 발생 시
            log.debug(
                    "Polling skipped or failed. Circuit may be OPEN. Error: {}", e.getMessage());
        }
    }

    /**
     * Circuit Breaker + Retry가 적용된 로그 소비 메서드
     *
     * <p>장애 시나리오: 1. Redis 연결 실패 → Retry (Exponential Backoff) 2. 계속 실패 → Circuit Breaker OPEN 3.
     * 10초 후 자동으로 HALF_OPEN → 재시도 4. 성공하면 CLOSED로 복귀
     *
     * @throws Exception Redis 연결 실패 시
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "fallbackConsumeLogs")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public void consumeLogsWithCircuitBreaker() {
        // Consumer Group에서 메시지 읽기
        List<MapRecord<String, Object, Object>> records = readFromStream();

        if (records == null || records.isEmpty()) {
            log.trace("No new messages in Redis Stream.");
            return;
        }

        log.info("Consumed {} messages from Redis Stream.", records.size());

        // LogBufferService에 전달
        for (MapRecord<String, Object, Object> record : records) {
            try {
                GameLog gameLog = parseRecord(record);
                logBufferService.add(gameLog, record.getId());
            } catch (Exception e) {
                log.error("Failed to parse or add log. RecordId: {}", record.getId(), e);
                // 파싱 실패한 메시지는 ACK 처리 (재처리 방지)
                logBufferService.acknowledgeFailedMessage(record.getId());
            }
        }
    }

    /**
     * Redis Streams에서 메시지 읽기
     *
     * <p>XREADGROUP 명령어 사용: - GROUP: Consumer Group 이름 - CONSUMER: 이 Consumer의 ID - COUNT: 최대 읽기
     * 개수 - BLOCK: 데이터가 없으면 대기 시간 (밀리초)
     *
     * @return 읽어온 메시지 리스트
     */
    private List<MapRecord<String, Object, Object>> readFromStream() {
        StreamReadOptions options =
                StreamReadOptions.empty()
                        .count(batchSize)
                        .block(Duration.ofMillis(pollBlockMs));

        return redisTemplate
                .opsForStream()
                .read(
                        Consumer.from(consumerGroup, consumerId),
                        options,
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
    }

    /**
     * MapRecord → GameLog 변환
     *
     * @param record Redis Stream 레코드
     * @return GameLog 엔티티
     */
    private GameLog parseRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();

        // Map<Object, Object> → Map<String, String> 변환
        Map<String, String> stringMap =
                value.entrySet().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        e -> String.valueOf(e.getKey()),
                                        e -> String.valueOf(e.getValue())));

        try {
            return logMapper.toEntity(stringMap);
        } catch (Exception e) {
            log.error("Failed to convert map to GameLog. Record: {}", record.getId(), e);
            throw new IllegalArgumentException("Invalid log format", e);
        }
    }

    /**
     * Circuit Breaker Fallback 메서드
     *
     * <p>Circuit이 OPEN 상태이거나 모든 재시도 실패 시 호출됨
     *
     * @param throwable 발생한 예외
     */
    @SuppressWarnings("unused")
    private void fallbackConsumeLogs(Exception throwable) {
        log.warn(
                "⚠️ Circuit Breaker OPEN or all retries failed. Skipping this polling cycle. Reason: {}",
                throwable.getMessage());

        // TODO: 긴급 알림 시스템 연동
        // - Slack/Email 알림
        // - 대시보드 경고 배너
        // - PagerDuty 호출 등
    }

    /**
     * Consumer Group 초기화 (앱 시작 시)
     *
     * <p>Consumer Group이 없으면 생성
     */
    @jakarta.annotation.PostConstruct
    public void initConsumerGroup() {
        try {
            // Consumer Group 생성 시도 (이미 있으면 예외 발생하지만 무시)
            redisTemplate
                    .opsForStream()
                    .createGroup(streamKey, ReadOffset.from("0"), consumerGroup);
            log.info(
                    "✅ Redis Consumer Group created: {} for stream: {}",
                    consumerGroup,
                    streamKey);
        } catch (Exception e) {
            // 이미 Consumer Group이 존재하면 예외 발생 (정상)
            log.info("Redis Consumer Group already exists: {}", consumerGroup);
        }
    }
}

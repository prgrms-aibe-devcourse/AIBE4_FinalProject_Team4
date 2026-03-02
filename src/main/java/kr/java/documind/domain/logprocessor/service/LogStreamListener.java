package kr.java.documind.domain.logprocessor.service;

import kr.java.documind.domain.logprocessor.model.entity.Log;
import java.time.Duration;
import java.util.List;
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
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogStreamListener {

    private final LogBufferService logBufferService;
    private final LogMapper logMapper;
    private final BackpressureManager backpressureManager;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${redis.stream.key}")
    private String streamKey;

    @Value("${redis.stream.group}")
    private String consumerGroup;

    @Value("${redis.stream.consumer}")
    private String consumerName;

    @Value("${worker.poll-interval-ms}")
    private long pollIntervalMs;

    @Value("${worker.poll-block-ms}")
    private long pollBlockMs;

    /** Scheduled Job: 설정된 주기마다 Redis Streams에서 로그 배치를 읽어옴 */
    @Scheduled(fixedDelayString = "${worker.poll-interval-ms}")
    @SuppressWarnings("unchecked")
    public void pollMessages() {
        applyBackpressure();

        try {
            // 동적 배치 크기 사용
            int batchSize = backpressureManager.getCurrentBatchSize();

            // StreamReadOptions 설정: COUNT와 BLOCK
            StreamReadOptions readOptions =
                    StreamReadOptions.empty()
                            .count(batchSize)
                            .block(Duration.ofMillis(pollBlockMs));

            // XREADGROUP: Consumer Group 방식으로 메시지 읽기
            List<MapRecord<String, String, String>> messages =
                    (List<MapRecord<String, String, String>>)
                            (List<?>)
                                    redisTemplate
                                            .opsForStream()
                                            .read(
                                                    Consumer.from(consumerGroup, consumerName),
                                                    readOptions,
                                                    StreamOffset.create(
                                                            streamKey, ReadOffset.lastConsumed()));

            if (messages == null || messages.isEmpty()) {
                log.debug("[Poll] No messages available (batchSize={})", batchSize);
                return;
            }

            log.info("[Poll] Received {} messages (batchSize={})", messages.size(), batchSize);

            // 각 메시지 처리
            for (MapRecord<String, String, String> message : messages) {
                try {
                    Log logEntity = logMapper.toEntity(message.getValue());
                    logBufferService.add(logEntity, message.getId());
                } catch (Exception e) {
                    // 보안: 민감 정보(value)는 로그에 남기지 않고 Message ID만 기록
                    log.error(
                            "Failed to process Redis Stream message. ID: {}", message.getId(), e);
                }
            }

        } catch (Exception e) {
            log.error("[Poll] Error during message polling", e);
        }
    }

    private void applyBackpressure() {
        long sleepMs = backpressureManager.getSleepMillis();
        if (sleepMs <= 0) {
            return;
        }

        log.warn(
                "[Backpressure] DB 지연 감지 (state={}, avg={} ms) - {} ms 대기",
                backpressureManager.getState(),
                String.format("%.1f", backpressureManager.getAvgLatencyMs()),
                sleepMs);

        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Backpressure] 대기 중 인터럽트 발생. 소비를 재개합니다.");
        }
    }
}

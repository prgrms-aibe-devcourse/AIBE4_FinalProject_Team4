package kr.java.documind.domain.logprocessor.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import kr.java.documind.domain.logprocessor.model.dto.request.RawLogRequest;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.repository.LogJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogBufferService {

    private final LogJdbcRepository logJdbcRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final BackpressureManager backpressureManager;
    private final MeterRegistry meterRegistry;
    private final LogMapper logMapper;
    private final IssueGroupingBatchService issueGroupingBatchService;
    private final ConcurrentLinkedQueue<LogWrapper> buffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<LogWrapper> deadLetterQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);
    private final AtomicBoolean isRetrying = new AtomicBoolean(false);

    @Value("${worker.bulk.size}")
    private int batchSize;

    @Value("${worker.buffer.max-size}")
    private int maxBufferSize;

    @Value("${worker.dlq.max-retry}")
    private int maxRetryCount;

    @Value("${redis.stream.key}")
    private String streamKey;

    @Value("${redis.stream.group}")
    private String consumerGroup;

    @PostConstruct
    public void init() {
        if (batchSize <= 0) {
            log.warn("Invalid batch size: {}. Resetting to 1000.", batchSize);
            batchSize = 1000;
        }

        Gauge.builder("worker.buffer.size", buffer, ConcurrentLinkedQueue::size)
                .description("인메모리 로그 버퍼 현재 크기")
                .register(meterRegistry);

        Gauge.builder("worker.dlq.size", deadLetterQueue, ConcurrentLinkedQueue::size)
                .description("Dead Letter Queue 현재 크기")
                .register(meterRegistry);
    }

    // API 요청용 (RecordId 없음)
    public void add(GameLog logEntity) {
        add(logEntity, null);
    }

    public void add(GameLog logEntity, RecordId recordId) {
        if (buffer.size() >= maxBufferSize) {
            log.warn("Buffer is full (size: {}). Dropping log and ACKing message.", buffer.size());
            // 버퍼 오버플로우 시 메시지를 ACK하여 PEL에서 제거
            if (recordId != null) {
                acknowledgeFailedMessage(recordId);
            }
            return;
        }

        buffer.offer(new LogWrapper(logEntity, recordId));
        // 동적 배치 크기 사용
        int dynamicBatchSize = backpressureManager.getCurrentBatchSize();
        if (buffer.size() >= dynamicBatchSize) {
            flush();
        }
    }

    // API 요청용 - DTO를 받아서 변환 (Service Layer에서 변환 처리)
    public void addFromDto(RawLogRequest dto) {
        GameLog logEntity = logMapper.toEntity(dto);
        add(logEntity);
    }

    // 일괄 처리용
    public void addAllFromDtos(List<RawLogRequest> dtos) {
        dtos.forEach(this::addFromDto);
    }

    @Scheduled(fixedDelayString = "${worker.bulk.flush-interval-ms}")
    @Transactional
    public void flush() {
        if (!isFlushing.compareAndSet(false, true)) {
            return;
        }

        try {
            if (buffer.isEmpty()) {
                return;
            }

            // 동적 배치 크기 사용
            int dynamicBatchSize = backpressureManager.getCurrentBatchSize();
            List<LogWrapper> wrappersToSave = new ArrayList<>();
            LogWrapper wrapper;
            while (wrappersToSave.size() < dynamicBatchSize && (wrapper = buffer.poll()) != null) {
                wrappersToSave.add(wrapper);
            }

            if (wrappersToSave.isEmpty()) {
                return;
            }

            List<GameLog> logs =
                    wrappersToSave.stream().map(LogWrapper::log).collect(Collectors.toList());

            try {
                long start = System.currentTimeMillis();
                logJdbcRepository.saveAll(logs);
                long latencyMs = System.currentTimeMillis() - start;
                backpressureManager.recordLatency(latencyMs);

                // 로그 저장 후 이슈 그룹핑 수행
                try {
                    issueGroupingBatchService.groupLogs(logs);
                } catch (Exception e) {
                    log.error(
                            "Failed to group logs into issues. Logs are saved but issues not created.",
                            e);
                    // 이슈 생성 실패해도 로그는 저장되었으므로 ACK는 보냄
                }

                // RecordId가 있는 경우에만 ACK 전송
                List<RecordId> recordIds =
                        wrappersToSave.stream()
                                .map(LogWrapper::recordId)
                                .filter(id -> id != null)
                                .collect(Collectors.toList());

                if (!recordIds.isEmpty()) {
                    redisTemplate
                            .opsForStream()
                            .acknowledge(
                                    streamKey, consumerGroup, recordIds.toArray(new RecordId[0]));
                }

                log.info(
                        "Flushed {} logs to DB in {}ms (state={}, ACK sent for {} items)",
                        logs.size(),
                        latencyMs,
                        backpressureManager.getState(),
                        recordIds.size());
            } catch (Exception e) {
                log.error(
                        "Failed to flush {} logs to DB. Moving to DLQ for retry.", logs.size(), e);
                // 실패한 로그들을 Dead Letter Queue에 추가
                wrappersToSave.forEach(
                        w -> {
                            LogWrapper retryWrapper =
                                    new LogWrapper(w.log(), w.recordId(), w.retryCount());
                            deadLetterQueue.offer(retryWrapper);
                        });
            }
        } finally {
            isFlushing.set(false);
        }
    }

    @Scheduled(fixedDelayString = "${worker.dlq.retry-interval-ms}")
    @Transactional
    public void retryDeadLetterQueue() {
        if (!isRetrying.compareAndSet(false, true)) {
            return;
        }

        try {
            if (deadLetterQueue.isEmpty()) {
                return;
            }

            // 동적 배치 크기 사용
            int dynamicBatchSize = backpressureManager.getCurrentBatchSize();
            List<LogWrapper> wrappersToRetry = new ArrayList<>();
            LogWrapper wrapper;
            while (wrappersToRetry.size() < dynamicBatchSize
                    && (wrapper = deadLetterQueue.poll()) != null) {
                wrappersToRetry.add(wrapper);
            }

            if (wrappersToRetry.isEmpty()) {
                return;
            }

            List<GameLog> logs =
                    wrappersToRetry.stream().map(LogWrapper::log).collect(Collectors.toList());

            try {
                long start = System.currentTimeMillis();
                logJdbcRepository.saveAll(logs);
                long latencyMs = System.currentTimeMillis() - start;

                // RecordId가 있는 경우에만 ACK 전송
                List<RecordId> recordIds =
                        wrappersToRetry.stream()
                                .map(LogWrapper::recordId)
                                .filter(id -> id != null)
                                .collect(Collectors.toList());

                if (!recordIds.isEmpty()) {
                    redisTemplate
                            .opsForStream()
                            .acknowledge(
                                    streamKey, consumerGroup, recordIds.toArray(new RecordId[0]));
                }

                log.info(
                        "[DLQ] Successfully retried {} logs to DB in {}ms (ACK sent for {} items)",
                        logs.size(),
                        latencyMs,
                        recordIds.size());
            } catch (Exception e) {
                log.error("[DLQ] Failed to retry {} logs to DB", logs.size(), e);

                // 재시도 횟수 증가 및 DLQ 재추가 또는 최종 실패 처리
                wrappersToRetry.forEach(
                        w -> {
                            int newRetryCount = w.retryCount() + 1;
                            if (newRetryCount < maxRetryCount) {
                                LogWrapper retryWrapper =
                                        new LogWrapper(w.log(), w.recordId(), newRetryCount);
                                deadLetterQueue.offer(retryWrapper);
                                log.warn(
                                        "[DLQ] Retry count: {}/{} for log ID: {}",
                                        newRetryCount,
                                        maxRetryCount,
                                        w.recordId());
                            } else {
                                handleFinalFailure(w);
                            }
                        });
            }
        } finally {
            isRetrying.set(false);
        }
    }

    private void handleFinalFailure(LogWrapper wrapper) {
        log.error(
                "[DLQ] Final failure after {} retries. RecordId: {}, Log: {}",
                maxRetryCount,
                wrapper.recordId(),
                wrapper.log());
        // TODO: 파일에 기록하거나 별도 알림 시스템 연동

        // 최종 실패 후에도 ACK 처리하여 PEL에서 제거
        if (wrapper.recordId() != null) {
            acknowledgeFailedMessage(wrapper.recordId());
        }
    }

    /**
     * 처리 실패한 메시지를 ACK하여 PEL에서 제거
     *
     * @param recordId 실패한 메시지의 RecordId
     */
    public void acknowledgeFailedMessage(RecordId recordId) {
        try {
            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, recordId);
            log.warn(
                    "[ACK] Failed message acknowledged to prevent PEL buildup. RecordId: {}",
                    recordId);
        } catch (Exception e) {
            log.error("[ACK] Failed to acknowledge message. RecordId: {}", recordId, e);
        }
    }
}

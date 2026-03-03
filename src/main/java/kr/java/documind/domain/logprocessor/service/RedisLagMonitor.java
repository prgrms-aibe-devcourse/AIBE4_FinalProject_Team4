package kr.java.documind.domain.logprocessor.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLagMonitor {

    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${redis.stream.key}")
    private String streamKey;

    @Value("${redis.stream.group}")
    private String consumerGroup;

    private final AtomicLong pendingCount = new AtomicLong(0);
    private final AtomicLong streamLength = new AtomicLong(0);

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("redis.stream.pending", pendingCount, AtomicLong::get)
                .description("Redis Stream 소비자 그룹의 미ACK 메시지 수")
                .tag("stream", streamKey)
                .tag("group", consumerGroup)
                .register(meterRegistry);

        Gauge.builder("redis.stream.length", streamLength, AtomicLong::get)
                .description("Redis Stream 전체 메시지 수 (XLEN)")
                .tag("stream", streamKey)
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${worker.lag-monitor.interval-ms}")
    public void collect() {
        collectPending();
        collectStreamLength();
    }

    private void collectPending() {
        try {
            PendingMessagesSummary summary =
                    redisTemplate.opsForStream().pending(streamKey, consumerGroup);

            long count = (summary != null) ? summary.getTotalPendingMessages() : 0L;
            pendingCount.set(count);
            log.debug("[LagMonitor] pending={}", count);
        } catch (Exception e) {
            log.warn("[LagMonitor] pending 조회 실패: {}", e.getMessage());
        }
    }

    private void collectStreamLength() {
        try {
            Long length = redisTemplate.opsForStream().size(streamKey);
            streamLength.set(length != null ? length : 0L);
            log.debug("[LagMonitor] stream.length={}", streamLength.get());
        } catch (Exception e) {
            log.warn("[LagMonitor] stream length 조회 실패: {}", e.getMessage());
        }
    }
}

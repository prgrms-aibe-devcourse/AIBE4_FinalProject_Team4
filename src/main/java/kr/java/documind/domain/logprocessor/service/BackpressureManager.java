package kr.java.documind.domain.logprocessor.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import kr.java.documind.domain.logprocessor.model.enums.BackpressureState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackpressureManager {

    /** EMA 평활 계수 (0 < alpha <= 1, 클수록 최신 값에 더 민감) */
    private static final double EMA_ALPHA = 0.3;

    private final MeterRegistry meterRegistry;

    @Value("${worker.backpressure.warn-threshold-ms}")
    private long warnThresholdMs;

    @Value("${worker.backpressure.critical-threshold-ms}")
    private long criticalThresholdMs;

    @Value("${worker.backpressure.warn-sleep-ms}")
    private long warnSleepMs;

    @Value("${worker.backpressure.critical-sleep-ms}")
    private long criticalSleepMs;

    // 배치 크기 동적 조절 관련
    @Value("${worker.backpressure.initial-batch-size}")
    private int initialBatchSize;

    @Value("${worker.backpressure.min-batch-size}")
    private int minBatchSize;

    @Value("${worker.backpressure.max-batch-size}")
    private int maxBatchSize;

    @Value("${worker.backpressure.increase-threshold-ms}")
    private long increaseThresholdMs;

    @Value("${worker.backpressure.decrease-threshold-ms}")
    private long decreaseThresholdMs;

    private volatile double avgLatencyMs = 0;
    private volatile int currentBatchSize;

    @PostConstruct
    public void registerGauges() {
        currentBatchSize = initialBatchSize;

        Gauge.builder("worker.db.latency.avg", this, BackpressureManager::getAvgLatencyMs)
                .description("DB Insert EMA 평균 지연 (ms)")
                .register(meterRegistry);

        Gauge.builder("worker.backpressure.state", this, bp -> bp.getState().ordinal())
                .description("Backpressure 상태 (0=NORMAL, 1=WARN, 2=CRITICAL)")
                .register(meterRegistry);

        Gauge.builder(
                        "worker.backpressure.batch.size",
                        this,
                        BackpressureManager::getCurrentBatchSize)
                .description("현재 동적 조절된 배치 크기")
                .register(meterRegistry);
    }

    public void recordLatency(long latencyMs) {
        avgLatencyMs = EMA_ALPHA * latencyMs + (1 - EMA_ALPHA) * avgLatencyMs;
        adjustBatchSize(latencyMs);
        log.debug(
                "[Backpressure] latency={}ms, avg={:.1f}ms, batchSize={}",
                latencyMs,
                avgLatencyMs,
                currentBatchSize);
    }

    /** 처리 시간 기반 배치 크기 동적 조절 - 5초 초과: 배치 크기 50% 감소 - 1초 미만: 배치 크기 증가 */
    private void adjustBatchSize(long latencyMs) {
        int previousBatchSize = currentBatchSize;

        if (latencyMs >= decreaseThresholdMs) {
            // 처리 시간이 5초 이상 → 배치 크기 감소
            currentBatchSize = Math.max(minBatchSize, currentBatchSize / 2);
            if (currentBatchSize != previousBatchSize) {
                log.warn(
                        "[Backpressure] 배치 크기 감소: {} → {} (latency={}ms)",
                        previousBatchSize,
                        currentBatchSize,
                        latencyMs);
            }
        } else if (latencyMs < increaseThresholdMs && avgLatencyMs < increaseThresholdMs) {
            // 처리 시간이 1초 미만 → 배치 크기 증가
            int increment = Math.max(10, currentBatchSize / 10); // 최소 10개씩 증가
            currentBatchSize = Math.min(maxBatchSize, currentBatchSize + increment);
            if (currentBatchSize != previousBatchSize) {
                log.info(
                        "[Backpressure] 배치 크기 증가: {} → {} (latency={}ms)",
                        previousBatchSize,
                        currentBatchSize,
                        latencyMs);
            }
        }
    }

    public long getSleepMillis() {
        if (avgLatencyMs >= criticalThresholdMs) {
            return criticalSleepMs;
        }
        if (avgLatencyMs >= warnThresholdMs) {
            return warnSleepMs;
        }
        return 0;
    }

    public BackpressureState getState() {
        if (avgLatencyMs >= criticalThresholdMs) {
            return BackpressureState.CRITICAL;
        }
        if (avgLatencyMs >= warnThresholdMs) {
            return BackpressureState.WARN;
        }
        return BackpressureState.NORMAL;
    }

    public double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public int getCurrentBatchSize() {
        return currentBatchSize;
    }
}

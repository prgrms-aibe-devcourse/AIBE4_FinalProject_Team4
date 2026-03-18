package kr.java.documind.domain.logprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kr.java.documind.domain.logprocessor.model.enums.BackpressureState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("BackpressureManager 단위 테스트")
class BackpressureManagerTest {

    private BackpressureManager backpressureManager;

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        backpressureManager = new BackpressureManager(meterRegistry);

        // 설정값 주입
        ReflectionTestUtils.setField(backpressureManager, "warnThresholdMs", 3000L);
        ReflectionTestUtils.setField(backpressureManager, "criticalThresholdMs", 5000L);
        ReflectionTestUtils.setField(backpressureManager, "warnSleepMs", 100L);
        ReflectionTestUtils.setField(backpressureManager, "criticalSleepMs", 500L);
        ReflectionTestUtils.setField(backpressureManager, "initialBatchSize", 100);
        ReflectionTestUtils.setField(backpressureManager, "minBatchSize", 10);
        ReflectionTestUtils.setField(backpressureManager, "maxBatchSize", 500);
        ReflectionTestUtils.setField(backpressureManager, "increaseThresholdMs", 1000L);
        ReflectionTestUtils.setField(backpressureManager, "decreaseThresholdMs", 5000L);

        backpressureManager.registerGauges();
    }

    @Test
    @DisplayName("초기 상태: NORMAL, 초기 배치 크기 100")
    void initialState() {
        // Then
        assertThat(backpressureManager.getState()).isEqualTo(BackpressureState.NORMAL);
        assertThat(backpressureManager.getCurrentBatchSize()).isEqualTo(100);
        assertThat(backpressureManager.getAvgLatencyMs()).isEqualTo(0);
        assertThat(backpressureManager.getSleepMillis()).isEqualTo(0);
    }

    @Test
    @DisplayName("처리 시간 5초 초과: 배치 크기 50% 감소 (100 → 50)")
    void decreaseBatchSizeWhenHighLatency() {
        // Given
        int initialBatchSize = backpressureManager.getCurrentBatchSize();

        // When: 처리 시간 6초 (5초 임계값 초과)
        backpressureManager.recordLatency(6000);

        // Then
        assertThat(backpressureManager.getCurrentBatchSize())
                .isEqualTo(initialBatchSize / 2)
                .isEqualTo(50);
    }

    @Test
    @DisplayName("처리 시간 1초 미만: 배치 크기 증가 (100 → 110)")
    void increaseBatchSizeWhenLowLatency() {
        // Given
        int initialBatchSize = backpressureManager.getCurrentBatchSize();

        // When: 처리 시간 800ms (1초 미만)
        backpressureManager.recordLatency(800);

        // Then
        assertThat(backpressureManager.getCurrentBatchSize())
                .isGreaterThan(initialBatchSize)
                .isEqualTo(110);
    }

    @Test
    @DisplayName("최소 배치 크기 제한: 10 이하로 감소하지 않음")
    void minimumBatchSizeLimit() {
        // Given: 배치 크기를 초기에 20으로 설정
        ReflectionTestUtils.setField(backpressureManager, "currentBatchSize", 20);

        // When: 연속으로 높은 지연 시간 발생
        backpressureManager.recordLatency(6000);
        backpressureManager.recordLatency(6000);

        // Then
        assertThat(backpressureManager.getCurrentBatchSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("WARN 상태 진입: 평균 지연 3초 이상")
    void warnStateWhenAvgLatencyHigh() {
        // Given & When: 평균 지연이 3초 이상 되도록 여러 번 기록
        backpressureManager.recordLatency(4000);
        backpressureManager.recordLatency(4000);
        backpressureManager.recordLatency(4000);

        // Then
        assertThat(backpressureManager.getState()).isEqualTo(BackpressureState.WARN);
        assertThat(backpressureManager.getSleepMillis()).isEqualTo(100L);
    }

    @Test
    @DisplayName("CRITICAL 상태 진입: 평균 지연 5초 이상")
    void criticalStateWhenAvgLatencyVeryHigh() {
        // Given & When: 평균 지연이 5초 이상 되도록 여러 번 기록
        backpressureManager.recordLatency(6000);
        backpressureManager.recordLatency(6000);
        backpressureManager.recordLatency(6000);

        // Then
        assertThat(backpressureManager.getState()).isEqualTo(BackpressureState.CRITICAL);
        assertThat(backpressureManager.getSleepMillis()).isEqualTo(500L);
    }

    @Test
    @DisplayName("배치 크기 동적 조절: 실전 시나리오")
    void dynamicBatchSizeAdjustmentScenario() {
        // Given: 초기 배치 크기 100

        // When 1: 처리 속도 빠름 (800ms) → 배치 크기 증가
        backpressureManager.recordLatency(800);
        int afterIncrease = backpressureManager.getCurrentBatchSize();
        assertThat(afterIncrease).isGreaterThan(100);

        // When 2: 처리 속도 저하 (6000ms) → 배치 크기 감소
        backpressureManager.recordLatency(6000);
        int afterDecrease = backpressureManager.getCurrentBatchSize();
        assertThat(afterDecrease).isLessThan(afterIncrease);

        // When 3: 다시 처리 속도 개선 (900ms) → 배치 크기 증가
        ReflectionTestUtils.setField(backpressureManager, "avgLatencyMs", 900.0);
        backpressureManager.recordLatency(900);
        int afterRecovery = backpressureManager.getCurrentBatchSize();
        assertThat(afterRecovery).isGreaterThan(afterDecrease);
    }
}

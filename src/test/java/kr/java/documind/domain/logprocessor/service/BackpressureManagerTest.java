package kr.java.documind.domain.logprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kr.java.documind.domain.logprocessor.model.enums.BackpressureState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("BackpressureManager 단위 테스트")
class BackpressureManagerTest {

    private BackpressureManager backpressureManager;
    private MeterRegistry meterRegistry;

    // 테스트용 설정값
    private static final long WARN_THRESHOLD_MS = 3000L;
    private static final long CRITICAL_THRESHOLD_MS = 5000L;
    private static final long WARN_SLEEP_MS = 1000L;
    private static final long CRITICAL_SLEEP_MS = 3000L;
    private static final int INITIAL_BATCH_SIZE = 100;
    private static final int MIN_BATCH_SIZE = 10;
    private static final int MAX_BATCH_SIZE = 500;
    private static final long INCREASE_THRESHOLD_MS = 1000L;
    private static final long DECREASE_THRESHOLD_MS = 5000L;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        backpressureManager = new BackpressureManager(meterRegistry);

        // ReflectionTestUtils를 사용하여 @Value 필드 초기화
        ReflectionTestUtils.setField(backpressureManager, "warnThresholdMs", WARN_THRESHOLD_MS);
        ReflectionTestUtils.setField(
                backpressureManager, "criticalThresholdMs", CRITICAL_THRESHOLD_MS);
        ReflectionTestUtils.setField(backpressureManager, "warnSleepMs", WARN_SLEEP_MS);
        ReflectionTestUtils.setField(backpressureManager, "criticalSleepMs", CRITICAL_SLEEP_MS);
        ReflectionTestUtils.setField(backpressureManager, "initialBatchSize", INITIAL_BATCH_SIZE);
        ReflectionTestUtils.setField(backpressureManager, "minBatchSize", MIN_BATCH_SIZE);
        ReflectionTestUtils.setField(backpressureManager, "maxBatchSize", MAX_BATCH_SIZE);
        ReflectionTestUtils.setField(
                backpressureManager, "increaseThresholdMs", INCREASE_THRESHOLD_MS);
        ReflectionTestUtils.setField(
                backpressureManager, "decreaseThresholdMs", DECREASE_THRESHOLD_MS);

        // @PostConstruct 메서드 수동 호출
        backpressureManager.registerGauges();
    }

    @Test
    @DisplayName("초기 상태: NORMAL 상태이고 배치 크기는 초기값")
    void initialState() {
        // Given: 초기 상태

        // When: 상태 조회
        BackpressureState state = backpressureManager.getState();
        int batchSize = backpressureManager.getCurrentBatchSize();
        long sleepMs = backpressureManager.getSleepMillis();

        // Then: NORMAL 상태, 초기 배치 크기, 대기 시간 0
        assertThat(state).isEqualTo(BackpressureState.NORMAL);
        assertThat(batchSize).isEqualTo(INITIAL_BATCH_SIZE);
        assertThat(sleepMs).isEqualTo(0);
    }

    @Test
    @DisplayName("배치 크기 증가: 1초 미만 처리 시 배치 크기 증가")
    void batchSizeIncrease_WhenFastProcessing() {
        // Given: 초기 배치 크기 100
        int initialSize = backpressureManager.getCurrentBatchSize();

        // When: 500ms 처리 시간을 5회 기록
        for (int i = 0; i < 5; i++) {
            backpressureManager.recordLatency(500L);
        }

        // Then: 배치 크기가 증가해야 함
        int finalSize = backpressureManager.getCurrentBatchSize();
        assertThat(finalSize).isGreaterThan(initialSize);
        assertThat(finalSize).isLessThanOrEqualTo(MAX_BATCH_SIZE);
    }

    @Test
    @DisplayName("배치 크기 감소: 5초 초과 처리 시 배치 크기 50% 감소")
    void batchSizeDecrease_WhenSlowProcessing() {
        // Given: 초기 배치 크기 100
        int initialSize = backpressureManager.getCurrentBatchSize();

        // When: 6000ms 처리 시간 기록
        backpressureManager.recordLatency(6000L);

        // Then: 배치 크기가 50% 감소 (100 → 50)
        int finalSize = backpressureManager.getCurrentBatchSize();
        assertThat(finalSize).isEqualTo(initialSize / 2);
    }

    @Test
    @DisplayName("배치 크기 최소값: 감소해도 최소값(10) 미만으로 내려가지 않음")
    void batchSizeMinimum_NotBelowMinimum() {
        // Given: 초기 배치 크기를 최소값 근처로 설정
        ReflectionTestUtils.setField(backpressureManager, "currentBatchSize", 20);

        // When: 느린 처리를 여러 번 기록
        for (int i = 0; i < 5; i++) {
            backpressureManager.recordLatency(6000L);
        }

        // Then: 최소값(10)으로 유지
        int finalSize = backpressureManager.getCurrentBatchSize();
        assertThat(finalSize).isEqualTo(MIN_BATCH_SIZE);
    }

    @Test
    @DisplayName("배치 크기 최대값: 증가해도 최대값(500) 초과하지 않음")
    void batchSizeMaximum_NotAboveMaximum() {
        // Given: 초기 배치 크기를 최대값 근처로 설정
        ReflectionTestUtils.setField(backpressureManager, "currentBatchSize", 490);

        // When: 빠른 처리를 여러 번 기록
        for (int i = 0; i < 10; i++) {
            backpressureManager.recordLatency(500L);
        }

        // Then: 최대값(500)으로 유지
        int finalSize = backpressureManager.getCurrentBatchSize();
        assertThat(finalSize).isEqualTo(MAX_BATCH_SIZE);
    }

    @Test
    @DisplayName("상태 전환: NORMAL → WARN (3초 이상 지연)")
    void stateTransition_NormalToWarn() {
        // Given: 초기 NORMAL 상태

        // When: 3500ms 처리 시간을 여러 번 기록 (EMA로 평균 올림)
        for (int i = 0; i < 15; i++) {
            backpressureManager.recordLatency(3500L);
        }

        // Then: WARN 상태로 전환
        BackpressureState state = backpressureManager.getState();
        long sleepMs = backpressureManager.getSleepMillis();

        assertThat(state).isEqualTo(BackpressureState.WARN);
        assertThat(sleepMs).isEqualTo(WARN_SLEEP_MS);
    }

    @Test
    @DisplayName("상태 전환: NORMAL → CRITICAL (5초 이상 지연)")
    void stateTransition_NormalToCritical() {
        // Given: 초기 NORMAL 상태

        // When: 6000ms 처리 시간을 여러 번 기록
        for (int i = 0; i < 15; i++) {
            backpressureManager.recordLatency(6000L);
        }

        // Then: CRITICAL 상태로 전환
        BackpressureState state = backpressureManager.getState();
        long sleepMs = backpressureManager.getSleepMillis();

        assertThat(state).isEqualTo(BackpressureState.CRITICAL);
        assertThat(sleepMs).isEqualTo(CRITICAL_SLEEP_MS);
    }

    @Test
    @DisplayName("상태 복귀: CRITICAL → NORMAL (빠른 처리로 복구)")
    void stateTransition_CriticalToNormal() {
        // Given: CRITICAL 상태로 만듦
        for (int i = 0; i < 15; i++) {
            backpressureManager.recordLatency(6000L);
        }
        assertThat(backpressureManager.getState()).isEqualTo(BackpressureState.CRITICAL);

        // When: 빠른 처리 시간을 여러 번 기록 (EMA로 평균 낮춤)
        for (int i = 0; i < 50; i++) {
            backpressureManager.recordLatency(500L);
        }

        // Then: NORMAL 상태로 복귀
        BackpressureState state = backpressureManager.getState();
        long sleepMs = backpressureManager.getSleepMillis();

        assertThat(state).isEqualTo(BackpressureState.NORMAL);
        assertThat(sleepMs).isEqualTo(0);
    }

    @Test
    @DisplayName("EMA 계산: 지수 이동 평균이 최신 값에 민감하게 반응")
    void emaCalculation_RespondsToRecentValues() {
        // Given: 초기 평균 0

        // When: 1000ms를 한 번 기록
        backpressureManager.recordLatency(1000L);
        double avgAfterFirst = backpressureManager.getAvgLatencyMs();

        // And: 5000ms를 한 번 기록
        backpressureManager.recordLatency(5000L);
        double avgAfterSecond = backpressureManager.getAvgLatencyMs();

        // Then: 평균이 최신 값 방향으로 이동
        assertThat(avgAfterFirst).isGreaterThan(0);
        assertThat(avgAfterSecond).isGreaterThan(avgAfterFirst);

        // EMA 계산: 0.3 * 1000 + 0.7 * 0 = 300
        assertThat(avgAfterFirst).isCloseTo(300.0, org.assertj.core.data.Offset.offset(1.0));

        // EMA 계산: 0.3 * 5000 + 0.7 * 300 = 1500 + 210 = 1710
        assertThat(avgAfterSecond).isCloseTo(1710.0, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    @DisplayName("메트릭 등록: Micrometer Gauge가 정상적으로 등록됨")
    void metricsRegistration_GaugesRegistered() {
        // Given: BackpressureManager 초기화 완료

        // When: 메트릭 조회
        Double avgLatencyGauge = meterRegistry.get("worker.db.latency.avg").gauge().value();
        Double stateGauge = meterRegistry.get("worker.backpressure.state").gauge().value();
        Double batchSizeGauge = meterRegistry.get("worker.backpressure.batch.size").gauge().value();

        // Then: Gauge가 정상적으로 등록되고 값 반환
        assertThat(avgLatencyGauge).isNotNull();
        assertThat(stateGauge).isNotNull();
        assertThat(batchSizeGauge).isNotNull();

        // And: 초기 상태값 검증
        assertThat(avgLatencyGauge).isEqualTo(0.0);
        assertThat(stateGauge).isEqualTo(0.0); // NORMAL = 0
        assertThat(batchSizeGauge).isEqualTo(INITIAL_BATCH_SIZE);
    }

    @Test
    @DisplayName("메트릭 업데이트: 지연 시간 기록 후 메트릭 값 변경")
    void metricsUpdate_AfterRecordingLatency() {
        // Given: 초기 메트릭 값
        Double initialAvg = meterRegistry.get("worker.db.latency.avg").gauge().value();

        // When: 지연 시간 기록
        backpressureManager.recordLatency(2000L);

        // Then: 메트릭 값이 업데이트됨
        Double updatedAvg = meterRegistry.get("worker.db.latency.avg").gauge().value();
        assertThat(updatedAvg).isGreaterThan(initialAvg);
    }
}

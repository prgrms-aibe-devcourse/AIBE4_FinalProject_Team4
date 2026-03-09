package kr.java.documind.domain.logprocessor.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.UUID;
import kr.java.documind.domain.logprocessor.config.SamplingConfig;
import kr.java.documind.domain.logprocessor.model.enums.BackpressureState;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("로그 샘플링 서비스 테스트 (Severity + HyperLogLog)")
class LogSamplingServiceTest {

    @Autowired private LogSamplingService logSamplingService;

    @Autowired private SamplingConfig samplingConfig;

    @Autowired private RedisTemplate<String, String> redisTemplate;

    @MockBean private BackpressureManager backpressureManager;

    private static final String TEST_FINGERPRINT = "test-fingerprint-12345";

    @BeforeEach
    void setUp() {
        // Redis HyperLogLog 키 초기화
        Set<String> keys = redisTemplate.keys("sampling:" + TEST_FINGERPRINT + ":hll:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // BackpressureManager Mock 초기화 (기본: NORMAL 상태)
        when(backpressureManager.getState()).thenReturn(BackpressureState.NORMAL);
    }

    @Test
    @DisplayName("샘플링 비활성화 시 모든 로그를 저장한다")
    void samplingDisabled_shouldSaveAllLogs() {
        // given
        samplingConfig.setEnabled(false);

        // when
        boolean shouldSample =
                logSamplingService.shouldSample(
                        TEST_FINGERPRINT, UUID.randomUUID().toString(), LogSeverity.INFO, null);

        // then
        assertThat(shouldSample).isFalse(); // false = 저장함
    }

    @Test
    @DisplayName("DEBUG 로그는 항상 Severity 비율로 샘플링된다")
    void debugLogs_alwaysSampledBySeverity() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.getSeverityRates().put("DEBUG", 0.0); // 0% 저장, 100% 샘플링

        int sampledCount = 0;

        // when: 100개 DEBUG 로그
        for (int i = 0; i < 100; i++) {
            boolean shouldSample =
                    logSamplingService.shouldSample(
                            TEST_FINGERPRINT,
                            UUID.randomUUID().toString(),
                            LogSeverity.DEBUG,
                            null);
            if (shouldSample) {
                sampledCount++;
            }
        }

        // then: 모두 샘플링
        assertThat(sampledCount).isEqualTo(100);
    }

    @Test
    @DisplayName("INFO 로그는 항상 Severity 비율로 샘플링된다")
    void infoLogs_alwaysSampledBySeverity() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.getSeverityRates().put("INFO", 0.1); // 10% 저장

        int savedCount = 0;
        int sampledCount = 0;

        // when: 1000개 INFO 로그
        for (int i = 0; i < 1000; i++) {
            boolean shouldSample =
                    logSamplingService.shouldSample(
                            TEST_FINGERPRINT, UUID.randomUUID().toString(), LogSeverity.INFO, null);
            if (shouldSample) {
                sampledCount++;
            } else {
                savedCount++;
            }
        }

        // then: 약 10% 저장, 90% 샘플링
        assertThat(savedCount).isBetween(80, 120); // 100 ± 20
        assertThat(sampledCount).isBetween(880, 920); // 900 ± 20
    }

    @Test
    @DisplayName("ERROR 로그는 Fingerprint/Backpressure 기준으로만 샘플링된다")
    void errorLogs_sampledByConditionOnly() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setThreshold(1000); // Fingerprint 임계값 높게
        samplingConfig.getSeverityRates().put("ERROR", 1.0); // Severity 기준 100% 저장

        // when: 10개 ERROR 로그 (임계값 미만)
        for (int i = 0; i < 10; i++) {
            boolean shouldSample =
                    logSamplingService.shouldSample(
                            TEST_FINGERPRINT,
                            UUID.randomUUID().toString(),
                            LogSeverity.ERROR,
                            null);
            assertThat(shouldSample).isFalse(); // 모두 저장
        }

        // then: HyperLogLog 카운트 확인
        long count = logSamplingService.getCurrentCount(TEST_FINGERPRINT);
        assertThat(count).isBetween(9L, 11L);
    }

    @Test
    @DisplayName("CRITICAL 로그는 기본적으로 모두 저장된다")
    void criticalLogs_alwaysSaved() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setThreshold(1000);
        samplingConfig.getSeverityRates().put("CRITICAL", 1.0);

        // when
        for (int i = 0; i < 50; i++) {
            boolean shouldSample =
                    logSamplingService.shouldSample(
                            TEST_FINGERPRINT,
                            UUID.randomUUID().toString(),
                            LogSeverity.FATAL,
                            null);
            assertThat(shouldSample).isFalse(); // 모두 저장
        }
    }

    @Test
    @DisplayName("ERROR 로그가 임계값 이하면 모두 저장된다")
    void errorBelowThreshold_shouldSaveAllLogs() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setThreshold(100);

        // when: 50회 호출 (임계값 이하, unique logId)
        for (int i = 0; i < 50; i++) {
            boolean shouldSample =
                    logSamplingService.shouldSample(
                            TEST_FINGERPRINT,
                            UUID.randomUUID().toString(),
                            LogSeverity.ERROR,
                            null);
            assertThat(shouldSample).isFalse(); // 모두 저장
        }

        // then: HyperLogLog 근사값 (0.81% 오차 허용)
        long count = logSamplingService.getCurrentCount(TEST_FINGERPRINT);
        assertThat(count).isBetween(49L, 51L); // 50 ± 1
    }

    @Test
    @DisplayName("ERROR 로그가 임계값 초과 시 샘플링이 적용된다")
    void errorAboveThreshold_shouldApplySampling() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setThreshold(10);
        samplingConfig.setRate(0.1); // 10% 저장, 90% 샘플링

        int totalCalls = 1000;
        int savedCount = 0;
        int sampledCount = 0;

        // when
        for (int i = 0; i < totalCalls; i++) {
            boolean shouldSample =
                    logSamplingService.shouldSample(
                            TEST_FINGERPRINT,
                            UUID.randomUUID().toString(),
                            LogSeverity.ERROR,
                            null);
            if (shouldSample) {
                sampledCount++;
            } else {
                savedCount++;
            }
        }

        // then
        // 처음 10개는 모두 저장, 나머지 990개 중 약 10%만 저장
        // 예상: 10 + 990 * 0.1 = 10 + 99 = 109개 저장
        // 실제로는 확률이므로 오차 범위 허용 (±30)
        assertThat(savedCount).isBetween(80, 140);
        assertThat(sampledCount).isGreaterThan(0);

        // HyperLogLog 카운트는 근사값 (0.81% 오차)
        // 1000개 기준 ±10 정도 오차 허용
        long count = logSamplingService.getCurrentCount(TEST_FINGERPRINT);
        assertThat(count).isBetween(990L, 1010L);
    }

    @Test
    @DisplayName("샘플링 비율 100%면 임계값 초과 후 모든 로그를 저장한다")
    void samplingRate100_shouldSaveAllLogs() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setThreshold(10);
        samplingConfig.setRate(1.0); // 100% 저장

        // when
        for (int i = 0; i < 100; i++) {
            boolean shouldSample =
                    logSamplingService.shouldSample(
                            TEST_FINGERPRINT,
                            UUID.randomUUID().toString(),
                            LogSeverity.ERROR,
                            null);
            assertThat(shouldSample).isFalse(); // 모두 저장
        }

        // then: HyperLogLog 근사값
        long count = logSamplingService.getCurrentCount(TEST_FINGERPRINT);
        assertThat(count).isBetween(98L, 102L); // 100 ± 2
    }

    @Test
    @DisplayName("샘플링 비율 0%면 임계값 초과 후 모든 로그를 샘플링한다")
    void samplingRate0_shouldSampleAllLogs() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setThreshold(10);
        samplingConfig.setRate(0.0); // 0% 저장, 100% 샘플링

        int savedCount = 0;
        int sampledCount = 0;

        // when
        for (int i = 0; i < 100; i++) {
            boolean shouldSample =
                    logSamplingService.shouldSample(
                            TEST_FINGERPRINT,
                            UUID.randomUUID().toString(),
                            LogSeverity.ERROR,
                            null);
            if (shouldSample) {
                sampledCount++;
            } else {
                savedCount++;
            }
        }

        // then
        // 처음 10개는 저장, 나머지 90개는 샘플링 (HyperLogLog 근사값으로 ±2 허용)
        assertThat(savedCount).isBetween(8, 12);
        assertThat(sampledCount).isBetween(88, 92);
    }

    @Test
    @DisplayName("Redis HyperLogLog는 윈도우 시간 후 자동으로 만료된다")
    void redisHyperLogLog_shouldExpireAfterWindow() throws InterruptedException {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setWindowSeconds(2); // 2초 윈도우

        // when
        logSamplingService.shouldSample(
                TEST_FINGERPRINT, UUID.randomUUID().toString(), LogSeverity.ERROR, null);
        long countBefore = logSamplingService.getCurrentCount(TEST_FINGERPRINT);
        assertThat(countBefore).isEqualTo(1);

        // 5초 대기 (윈도우 * 2 초과하여 TTL 만료)
        Thread.sleep(5000);

        // then
        long countAfter = logSamplingService.getCurrentCount(TEST_FINGERPRINT);
        assertThat(countAfter).isEqualTo(0); // TTL 만료로 HyperLogLog 삭제됨
    }

    @Test
    @DisplayName("서로 다른 fingerprint는 독립적으로 카운팅된다")
    void differentFingerprints_shouldCountIndependently() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setThreshold(100);

        String fingerprint1 = "fingerprint-1";
        String fingerprint2 = "fingerprint-2";

        // when
        for (int i = 0; i < 50; i++) {
            logSamplingService.shouldSample(
                    fingerprint1, UUID.randomUUID().toString(), LogSeverity.ERROR, null);
        }
        for (int i = 0; i < 30; i++) {
            logSamplingService.shouldSample(
                    fingerprint2, UUID.randomUUID().toString(), LogSeverity.ERROR, null);
        }

        // then: HyperLogLog 근사값
        assertThat(logSamplingService.getCurrentCount(fingerprint1)).isBetween(48L, 52L);
        assertThat(logSamplingService.getCurrentCount(fingerprint2)).isBetween(28L, 32L);

        // 정리
        Set<String> keys1 = redisTemplate.keys("sampling:" + fingerprint1 + ":hll:*");
        Set<String> keys2 = redisTemplate.keys("sampling:" + fingerprint2 + ":hll:*");
        if (keys1 != null) redisTemplate.delete(keys1);
        if (keys2 != null) redisTemplate.delete(keys2);
    }

    @Test
    @DisplayName("동일 logId는 HyperLogLog에서 중복 제거된다")
    void duplicateLogId_shouldBeDeduplicatedByHyperLogLog() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setThreshold(100);

        String duplicateLogId = UUID.randomUUID().toString();

        // when: 동일 logId를 50번 추가
        for (int i = 0; i < 50; i++) {
            logSamplingService.shouldSample(
                    TEST_FINGERPRINT, duplicateLogId, LogSeverity.ERROR, null);
        }

        // then: HyperLogLog는 unique 카운트만 추적하므로 1개로 카운팅
        long count = logSamplingService.getCurrentCount(TEST_FINGERPRINT);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("서버 과부하(WARN) 시 샘플링이 적용된다")
    void serverOverload_warn_shouldApplySampling() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setBackpressureEnabled(true);
        samplingConfig.setThreshold(1000); // Fingerprint 임계값은 높게 설정
        samplingConfig.setBackpressureRate(0.0); // 0% 저장, 100% 샘플링

        // 서버 WARN 상태로 설정
        when(backpressureManager.getState()).thenReturn(BackpressureState.WARN);

        int sampledCount = 0;

        // when: 10개 로그 전송 (Fingerprint 임계값 미만)
        for (int i = 0; i < 10; i++) {
            boolean shouldSample =
                    logSamplingService.shouldSample(
                            TEST_FINGERPRINT,
                            UUID.randomUUID().toString(),
                            LogSeverity.ERROR,
                            null);
            if (shouldSample) {
                sampledCount++;
            }
        }

        // then: Fingerprint 임계값은 안 넘었지만 서버 과부하로 샘플링 적용
        assertThat(sampledCount).isEqualTo(10); // 모두 샘플링
    }

    @Test
    @DisplayName("서버 과부하(CRITICAL) 시 더 공격적으로 샘플링된다")
    void serverOverload_critical_shouldApplyAggressiveSampling() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setBackpressureEnabled(true);
        samplingConfig.setThreshold(1000);
        samplingConfig.setBackpressureRate(0.0); // 0% 저장

        // 서버 CRITICAL 상태로 설정
        when(backpressureManager.getState()).thenReturn(BackpressureState.CRITICAL);

        int sampledCount = 0;

        // when
        for (int i = 0; i < 50; i++) {
            boolean shouldSample =
                    logSamplingService.shouldSample(
                            TEST_FINGERPRINT,
                            UUID.randomUUID().toString(),
                            LogSeverity.ERROR,
                            null);
            if (shouldSample) {
                sampledCount++;
            }
        }

        // then: 모두 샘플링
        assertThat(sampledCount).isEqualTo(50);
    }

    @Test
    @DisplayName("서버 부하 샘플링 비활성화 시 서버 상태를 무시한다")
    void backpressureDisabled_ignoresServerState() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setBackpressureEnabled(false); // 서버 부하 샘플링 비활성화
        samplingConfig.setThreshold(1000);

        // 서버 CRITICAL 상태여도 무시
        when(backpressureManager.getState()).thenReturn(BackpressureState.CRITICAL);

        // when
        boolean shouldSample =
                logSamplingService.shouldSample(
                        TEST_FINGERPRINT, UUID.randomUUID().toString(), LogSeverity.ERROR, null);

        // then: Fingerprint 임계값 미만이고 서버 부하 샘플링 비활성화이므로 모두 저장
        assertThat(shouldSample).isFalse();
    }

    @Test
    @DisplayName("Fingerprint 초과 OR 서버 과부하 시 샘플링된다 (하이브리드)")
    void hybrid_fingerprintOrBackpressure_shouldSample() {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setBackpressureEnabled(true);
        samplingConfig.setThreshold(5); // Fingerprint 임계값 낮게
        samplingConfig.setRate(0.0); // Fingerprint 샘플링 비율
        samplingConfig.setBackpressureRate(0.0); // 서버 부하 샘플링 비율

        int sampledCountFingerprint = 0;
        int sampledCountBackpressure = 0;

        // Case 1: Fingerprint 초과 (서버 정상)
        when(backpressureManager.getState()).thenReturn(BackpressureState.NORMAL);
        for (int i = 0; i < 10; i++) {
            boolean shouldSample =
                    logSamplingService.shouldSample(
                            "fingerprint-test",
                            UUID.randomUUID().toString(),
                            LogSeverity.ERROR,
                            null);
            if (shouldSample) {
                sampledCountFingerprint++;
            }
        }

        // Case 2: 서버 과부하 (Fingerprint 정상)
        when(backpressureManager.getState()).thenReturn(BackpressureState.WARN);
        for (int i = 0; i < 3; i++) {
            boolean shouldSample =
                    logSamplingService.shouldSample(
                            "another-fingerprint",
                            UUID.randomUUID().toString(),
                            LogSeverity.ERROR,
                            null);
            if (shouldSample) {
                sampledCountBackpressure++;
            }
        }

        // then
        assertThat(sampledCountFingerprint).isGreaterThan(0); // Fingerprint 초과로 샘플링
        assertThat(sampledCountBackpressure).isEqualTo(3); // 서버 과부하로 샘플링

        // 정리
        redisTemplate.delete(redisTemplate.keys("sampling:fingerprint-test:hll:*"));
        redisTemplate.delete(redisTemplate.keys("sampling:another-fingerprint:hll:*"));
    }
}

package kr.java.documind.domain.logprocessor.service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import kr.java.documind.domain.issue.service.tracking.UserCountTracker;
import kr.java.documind.domain.logprocessor.config.SamplingConfig;
import kr.java.documind.domain.logprocessor.model.enums.BackpressureState;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 로그 샘플링 서비스
 *
 * <p>Redis HyperLogLog 기반으로 fingerprint별 로그 발생 빈도를 실시간 추적하여 샘플링 여부를 결정
 *
 * <p>샘플링 전략 (우선순위 순): 1. Severity 기반: DEBUG/INFO는 항상 샘플링, ERROR/FATAL은 기본 보존 2. Fingerprint 기반: 동일
 * 에러 대량 발생 시 샘플링 3. 서버 부하 기반: DB 지연, 메모리 부족 시 샘플링
 *
 * <p>HyperLogLog 장점: - 메모리 효율: 고정 12KB로 수십억 개의 unique 값 추정 - 정확도: 0.81% 오차 범위 (대량 로그 환경에서 충분) -
 * 확장성: fingerprint가 수만 개여도 메모리 안정적
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogSamplingService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SamplingConfig samplingConfig;
    private final BackpressureManager backpressureManager;
    private final UserCountTracker userCountTracker;

    /** HyperLogLog Key 접두사 (샘플링 판단용) */
    @Value("${redis.hll.sampling-prefix}")
    private String hllKeyPrefix;

    /**
     * 로그를 샘플링해야 하는지 결정 (Severity + Fingerprint + Backpressure)
     *
     * <p>샘플링 우선순위: 1. Severity 기반 (DEBUG/INFO는 항상 샘플링, ERROR/CRITICAL은 보존) 2. Fingerprint 기반 (동일 에러
     * 대량 발생) 3. 서버 부하 기반 (DB 지연, 메모리 부족)
     *
     * <p>⚠️ 중요: 샘플링 판단 전에 userId를 HyperLogLog에 기록하여 영향받은 사용자 수를 정확히 추적
     *
     * @param fingerprint 로그 fingerprint
     * @param logId 로그 고유 ID (HyperLogLog에 추가할 unique 값)
     * @param severity 로그 심각도
     * @param userId 사용자 ID (영향받은 사용자 수 추적용, null 가능)
     * @return true면 샘플링(저장 안 함), false면 저장
     */
    public boolean shouldSample(
            String fingerprint, String logId, LogSeverity severity, String userId) {
        // 샘플링 판단 전에 userId를 먼저 기록 (샘플링 여부와 무관하게 모든 로그의 userId 추적)
        if (userId != null && !userId.isBlank()) {
            userCountTracker.addUser(fingerprint, userId);
        }

        // 샘플링 비활성화 상태면 모든 로그 저장
        if (!samplingConfig.isEnabled()) {
            return false;
        }

        try {
            // 1. Severity 기반 샘플링 (우선순위 1)
            // DEBUG/INFO는 항상 Severity 비율로 샘플링
            if (severity == LogSeverity.DEBUG || severity == LogSeverity.INFO) {
                return sampleBySeverity(severity, fingerprint, logId);
            }

            // 2. ERROR/FATAL은 Fingerprint + Backpressure만 체크
            if (severity == LogSeverity.ERROR || severity == LogSeverity.FATAL) {
                return sampleByCondition(fingerprint, logId, severity);
            }

            // 3. WARN은 Severity OR Fingerprint/Backpressure
            if (severity == LogSeverity.WARN) {
                // Severity 샘플링 적용
                boolean severitySampled = sampleBySeverity(severity, fingerprint, logId);
                if (severitySampled) {
                    return true; // Severity 기준으로 샘플링됨
                }

                // Severity 기준으로는 저장되었지만, Fingerprint/Backpressure 체크
                return sampleByCondition(fingerprint, logId, severity);
            }

            // 기본: 저장
            return false;
        } catch (Exception e) {
            log.error("[Sampling] Failed to check sampling. Fallback to save all logs.", e);
            // Redis 장애 시 모든 로그 저장 (안전한 fallback)
            return false;
        }
    }

    /**
     * Severity 기반 샘플링
     *
     * @param severity 로그 심각도
     * @param fingerprint 로그 fingerprint
     * @param logId 로그 ID
     * @return true면 샘플링(저장 안 함), false면 저장
     */
    private boolean sampleBySeverity(LogSeverity severity, String fingerprint, String logId) {
        double severityRate = samplingConfig.getSeverityRate(severity.toString());

        // HyperLogLog에 추가 (카운팅용)
        addToHyperLogLog(fingerprint, logId);

        // 확률적으로 샘플링 결정
        double randomValue = ThreadLocalRandom.current().nextDouble();
        boolean shouldDrop = randomValue >= severityRate;

        if (shouldDrop) {
            log.debug(
                    "[Sampling] Log dropped by severity. severity={}, rate={}, fingerprint={}, logId={}",
                    severity,
                    severityRate,
                    fingerprint,
                    logId);
        }

        return shouldDrop;
    }

    /**
     * Fingerprint + Backpressure 기반 샘플링
     *
     * @param fingerprint 로그 fingerprint
     * @param logId 로그 ID
     * @param severity 로그 심각도
     * @return true면 샘플링(저장 안 함), false면 저장
     */
    private boolean sampleByCondition(String fingerprint, String logId, LogSeverity severity) {
        // Fingerprint 기반 임계값 체크
        long count = addToHyperLogLog(fingerprint, logId);
        boolean fingerprintExceedsThreshold = count > samplingConfig.getThreshold();

        // 서버 부하 기반 체크
        boolean serverIsOverloaded = isServerOverloaded();

        // 둘 다 정상이면 모든 로그 저장
        if (!fingerprintExceedsThreshold && !serverIsOverloaded) {
            return false;
        }

        // 샘플링 비율 결정
        double samplingRate;
        String reason;

        if (serverIsOverloaded) {
            // 서버 과부하 시 더 공격적으로 샘플링
            samplingRate = samplingConfig.getBackpressureRate();
            reason =
                    "server-overload (state="
                            + backpressureManager.getState()
                            + ", rate="
                            + samplingRate
                            + ")";
        } else {
            // Fingerprint 임계값 초과 시 일반 샘플링
            samplingRate = samplingConfig.getRate();
            reason =
                    "fingerprint-threshold (count="
                            + count
                            + ", threshold="
                            + samplingConfig.getThreshold()
                            + ", rate="
                            + samplingRate
                            + ")";
        }

        // 확률적으로 샘플링 결정
        double randomValue = ThreadLocalRandom.current().nextDouble();
        boolean shouldDrop = randomValue >= samplingRate;

        if (shouldDrop) {
            log.debug(
                    "[Sampling] Log dropped by condition. reason={}, severity={}, fingerprint={}, logId={}",
                    reason,
                    severity,
                    fingerprint,
                    logId);
        }

        return shouldDrop;
    }

    /**
     * 서버 부하 상태 확인
     *
     * <p>BackpressureManager의 상태를 체크하여 서버 과부하 여부 판단
     *
     * @return true면 서버 과부하, false면 정상
     */
    private boolean isServerOverloaded() {
        if (!samplingConfig.isBackpressureEnabled()) {
            return false;
        }

        BackpressureState state = backpressureManager.getState();
        // WARN 또는 CRITICAL 상태면 서버 과부하로 판단
        return state == BackpressureState.WARN || state == BackpressureState.CRITICAL;
    }

    /**
     * HyperLogLog에 logId 추가 및 카운트 조회
     *
     * <p>키 형식: sampling:{fingerprint}:hll:{window} window: 현재 timestamp / windowSeconds (윈도우 단위)
     * TTL: windowSeconds * 2 (여유있게)
     *
     * @param fingerprint 로그 fingerprint
     * @param logId 로그 고유 ID
     * @return HyperLogLog 추정 카운트
     */
    private long addToHyperLogLog(String fingerprint, String logId) {
        String key = buildHllKey(fingerprint);

        // HyperLogLog에 logId 추가
        redisTemplate.opsForHyperLogLog().add(key, logId);

        // TTL 설정 (윈도우 * 2 만큼 유지)
        Long ttl = redisTemplate.getExpire(key);
        if (ttl == null || ttl == -1) {
            redisTemplate.expire(key, Duration.ofSeconds(samplingConfig.getWindowSeconds() * 2));
        }

        // HyperLogLog 카운트 조회
        Long count = redisTemplate.opsForHyperLogLog().size(key);
        return count != null ? count : 0L;
    }

    /**
     * HyperLogLog 키 생성
     *
     * <p>키 형식: sampling:{fingerprint}:hll:{window} window: 현재 윈도우 번호 (timestamp / windowSeconds)
     *
     * @param fingerprint 로그 fingerprint
     * @return HyperLogLog 키
     */
    private String buildHllKey(String fingerprint) {
        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        long window = currentTimeSeconds / samplingConfig.getWindowSeconds();
        return hllKeyPrefix + fingerprint + ":hll:" + window;
    }

    /**
     * 샘플링 통계 조회 (모니터링용)
     *
     * @param fingerprint 로그 fingerprint
     * @return 현재 윈도우 내 HyperLogLog 추정 카운트
     */
    public long getCurrentCount(String fingerprint) {
        String key = buildHllKey(fingerprint);
        Long count = redisTemplate.opsForHyperLogLog().size(key);
        return count != null ? count : 0L;
    }
}

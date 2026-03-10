package kr.java.documind.domain.issue.service.tracking;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis HyperLogLog를 사용한 영향받은 플레이어 수 추적 + Redis 카운터를 사용한 전체 로그 유입량 추적
 *
 * <p>HyperLogLog 장점:
 *
 * <ul>
 *   <li>메모리 효율: 12KB 고정 메모리로 수십억 개의 unique 값 카운팅
 *   <li>빠른 성능: O(1) 시간 복잡도
 *   <li>0.81% 표준 오차 (실용적으로 충분히 정확)
 * </ul>
 *
 * <p>Key 패턴:
 *
 * <ul>
 *   <li>플레이어 수: {@code issue:users:{fingerprint}:hll}
 *   <li>전체 로그 수: {@code total_logs:{projectId}:{yyyy-MM-dd-HH}}
 * </ul>
 *
 * <p>주의사항:
 *
 * <ul>
 *   <li>Redis 장애 시 데이터 유실 가능 (심각도 계산용이므로 허용)
 *   <li>정확한 값이 아닌 근사값 (0.81% 오차)
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCountTracker {

    private final RedisTemplate<String, String> redisTemplate;

    /** HyperLogLog Key 접두사 */
    private static final String KEY_PREFIX = "issue:users:";

    /** HyperLogLog Key 접미사 */
    private static final String KEY_SUFFIX = ":hll";

    /** 전체 로그 카운터 Key 접두사 */
    private static final String TOTAL_LOGS_PREFIX = "total_logs:";

    /** 시간 포맷 (시간 단위) */
    private static final DateTimeFormatter HOUR_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");

    /**
     * 특정 이슈에 영향받은 사용자 추가
     *
     * <p>동일한 userId를 여러 번 추가해도 1명으로 카운팅됨 (HyperLogLog 특성)
     *
     * <p>TTL 7일 자동 설정 (전체 로그 카운터와 동일한 보관 정책)
     *
     * @param fingerprint 이슈 고유 지문
     * @param userId 사용자 ID
     */
    public void addUser(String fingerprint, String userId) {
        if (fingerprint == null || fingerprint.isBlank()) {
            log.warn("Fingerprint가 null 또는 빈 문자열입니다. userId 추가 생략");
            return;
        }

        if (userId == null || userId.isBlank()) {
            log.warn("userId가 null 또는 빈 문자열입니다. Fingerprint: {}", fingerprint);
            return;
        }

        try {
            String key = buildKey(fingerprint);
            redisTemplate.opsForHyperLogLog().add(key, userId);

            // TTL 설정 (없거나 -1인 경우에만 설정)
            Long ttl = redisTemplate.getExpire(key);
            if (ttl == null || ttl == -1) {
                redisTemplate.expire(key, Duration.ofDays(7));
                log.debug("HyperLogLog TTL 설정: key={}, ttl=7일", key);
            }

            log.debug("사용자 추가 완료: Fingerprint={}, userId={}, key={}", fingerprint, userId, key);
        } catch (Exception e) {
            log.error("사용자 추가 실패 - Fingerprint: {}, userId: {}", fingerprint, userId, e);
            // Redis 장애 시에도 메인 로직은 계속 진행되어야 하므로 예외 전파하지 않음
        }
    }

    /**
     * 특정 이슈에 영향받은 사용자 수 조회
     *
     * @param fingerprint 이슈 고유 지문
     * @return 영향받은 unique 사용자 수 (근사값)
     */
    public long getAffectedUserCount(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            log.warn("Fingerprint가 null 또는 빈 문자열입니다. 0 반환");
            return 0L;
        }

        try {
            String key = buildKey(fingerprint);
            Long count = redisTemplate.opsForHyperLogLog().size(key);

            if (count == null) {
                log.debug("HyperLogLog 데이터 없음: Fingerprint={}", fingerprint);
                return 0L;
            }

            log.debug("사용자 수 조회 완료: Fingerprint={}, count={}, key={}", fingerprint, count, key);
            return count;

        } catch (Exception e) {
            log.error("사용자 수 조회 실패 - Fingerprint: {}", fingerprint, e);
            // Redis 장애 시 0 반환 (심각도 계산 시 플레이어 수 점수 0점 처리)
            return 0L;
        }
    }

    /**
     * 특정 이슈의 사용자 추적 데이터 삭제
     *
     * <p>이슈 해결 후 또는 테스트 데이터 정리 시 사용
     *
     * @param fingerprint 이슈 고유 지문
     * @return 삭제 성공 여부
     */
    public boolean deleteUserCount(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            log.warn("Fingerprint가 null 또는 빈 문자열입니다. 삭제 생략");
            return false;
        }

        try {
            String key = buildKey(fingerprint);
            Boolean deleted = redisTemplate.delete(key);

            log.info("사용자 추적 데이터 삭제: Fingerprint={}, deleted={}", fingerprint, deleted);
            return Boolean.TRUE.equals(deleted);

        } catch (Exception e) {
            log.error("사용자 추적 데이터 삭제 실패 - Fingerprint: {}", fingerprint, e);
            return false;
        }
    }

    /**
     * Redis Key 생성
     *
     * @param fingerprint 이슈 고유 지문
     * @return Redis Key (예: "issue:users:abc123:hll")
     */
    private String buildKey(String fingerprint) {
        return KEY_PREFIX + fingerprint + KEY_SUFFIX;
    }

    /**
     * 전체 로그 유입량 추적 (시간당 집계)
     *
     * <p>Redis Counter를 사용하여 프로젝트별 시간당 전체 로그 수 추적
     *
     * <p>7일 TTL 제약: FrequencyStrategy는 최근 7일 데이터만 사용하여 에러율 계산
     *
     * @param projectId 프로젝트 ID
     * @param timestamp 로그 발생 시각
     */
    public void trackTotalLogs(UUID projectId, OffsetDateTime timestamp) {
        if (projectId == null || timestamp == null) {
            log.warn("projectId 또는 timestamp가 null입니다. 전체 로그 추적 생략");
            return;
        }

        try {
            String key = buildTotalLogsKey(projectId, timestamp);
            redisTemplate.opsForValue().increment(key);

            // 7일 보관 (FrequencyStrategy는 최근 7일로 계산 범위 제한)
            redisTemplate.expire(key, Duration.ofDays(7));

            log.debug("전체 로그 카운터 증가: key={}", key);
        } catch (Exception e) {
            log.error("전체 로그 추적 실패 - projectId: {}, timestamp: {}", projectId, timestamp, e);
            // Redis 장애 시에도 메인 로직은 계속 진행되어야 하므로 예외 전파하지 않음
        }
    }

    /**
     * 특정 시간 범위의 전체 로그 수 조회
     *
     * <p>FrequencyStrategy에서 에러율 계산 시 사용 (최근 7일 이내 범위)
     *
     * <p>주의: 7일 이전 데이터는 TTL로 인해 조회 불가
     *
     * @param projectId 프로젝트 ID
     * @param start 시작 시각 (7일 이내 권장)
     * @param end 종료 시각
     * @return 시간 범위 내 전체 로그 수
     */
    public long getTotalLogsInTimeRange(UUID projectId, OffsetDateTime start, OffsetDateTime end) {
        if (projectId == null || start == null || end == null) {
            log.warn("projectId, start, end 중 null 값 존재. 0 반환");
            return 0L;
        }

        try {
            long totalLogs = 0L;
            OffsetDateTime current = start;

            // 시작 시각부터 종료 시각까지 시간 단위로 합산
            while (!current.isAfter(end)) {
                String key = buildTotalLogsKey(projectId, current);
                String value = redisTemplate.opsForValue().get(key);

                if (value != null) {
                    totalLogs += Long.parseLong(value);
                }

                current = current.plusHours(1);
            }

            log.debug(
                    "전체 로그 수 조회 완료: projectId={}, start={}, end={}, totalLogs={}",
                    projectId,
                    start,
                    end,
                    totalLogs);
            return totalLogs;

        } catch (Exception e) {
            log.error(
                    "전체 로그 수 조회 실패 - projectId: {}, start: {}, end: {}",
                    projectId,
                    start,
                    end,
                    e);
            // Redis 장애 시 0 반환 (fallback)
            return 0L;
        }
    }

    /**
     * 전체 로그 카운터 Redis Key 생성
     *
     * @param projectId 프로젝트 ID
     * @param timestamp 로그 발생 시각
     * @return Redis Key (예: "total_logs:uuid:2026-03-10-14")
     */
    private String buildTotalLogsKey(UUID projectId, OffsetDateTime timestamp) {
        String hourKey = timestamp.format(HOUR_FORMATTER);
        return TOTAL_LOGS_PREFIX + projectId + ":" + hourKey;
    }
}

package kr.java.documind.domain.issue.service.tracking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis HyperLogLog를 사용한 영향받은 플레이어 수 추적
 *
 * <p>HyperLogLog 장점:
 *
 * <ul>
 *   <li>메모리 효율: 12KB 고정 메모리로 수십억 개의 unique 값 카운팅
 *   <li>빠른 성능: O(1) 시간 복잡도
 *   <li>0.81% 표준 오차 (실용적으로 충분히 정확)
 * </ul>
 *
 * <p>Key 패턴: {@code issue:users:{fingerprint}:hll}
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

    /**
     * 특정 이슈에 영향받은 사용자 추가
     *
     * <p>동일한 userId를 여러 번 추가해도 1명으로 카운팅됨 (HyperLogLog 특성)
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
}

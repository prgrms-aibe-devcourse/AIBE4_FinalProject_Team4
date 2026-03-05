package kr.java.documind.global.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Streams Health Indicator
 *
 * <p>Spring Boot Actuator의 /actuator/health 엔드포인트에 노출
 *
 * <p>체크 항목: - Redis 연결 상태 - Circuit Breaker 상태 (CLOSED/HALF_OPEN/OPEN) - Circuit Breaker 메트릭
 * (실패율, 호출 횟수 등)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamHealthIndicator implements HealthIndicator {

    private static final String CIRCUIT_BREAKER_NAME = "redisStreamConsumer";

    private final RedisTemplate<String, String> redisTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Health Check 수행
     *
     * <p>Circuit Breaker가 OPEN 상태면 DOWN으로 표시
     *
     * @return Health 객체
     */
    @Override
    public Health health() {
        try {
            // Circuit Breaker 조회
            CircuitBreaker circuitBreaker =
                    circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);

            CircuitBreaker.State state = circuitBreaker.getState();
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();

            // Circuit Breaker 상태별 Health 설정
            Health.Builder healthBuilder;

            switch (state) {
                case CLOSED:
                    healthBuilder = Health.up();
                    break;
                case HALF_OPEN:
                    healthBuilder =
                            Health.status("RECOVERING")
                                    .withDetail("message", "Circuit is recovering...");
                    break;
                case OPEN:
                    healthBuilder =
                            Health.down()
                                    .withDetail(
                                            "reason",
                                            "Circuit Breaker is OPEN due to high failure rate");
                    break;
                default:
                    healthBuilder =
                            Health.unknown().withDetail("reason", "Unknown circuit state");
            }

            // Circuit Breaker 메트릭 추가
            Map<String, Object> circuitDetails = new HashMap<>();
            circuitDetails.put("state", state.name());
            circuitDetails.put("failureRate", String.format("%.2f%%", metrics.getFailureRate()));
            circuitDetails.put("numberOfFailedCalls", metrics.getNumberOfFailedCalls());
            circuitDetails.put("numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls());
            circuitDetails.put("numberOfBufferedCalls", metrics.getNumberOfBufferedCalls());

            // Redis 연결 테스트
            boolean redisConnected = testRedisConnection();
            circuitDetails.put("redisConnected", redisConnected);

            return healthBuilder.withDetails(circuitDetails).build();

        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down().withException(e).build();
        }
    }

    /**
     * Redis 연결 테스트
     *
     * <p>PING 명령어로 간단하게 확인
     *
     * @return 연결 성공 여부
     */
    private boolean testRedisConnection() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            log.debug("Redis connection test failed: {}", e.getMessage());
            return false;
        }
    }
}

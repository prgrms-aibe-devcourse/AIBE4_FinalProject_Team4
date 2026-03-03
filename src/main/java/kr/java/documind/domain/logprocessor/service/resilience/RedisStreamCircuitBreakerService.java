package kr.java.documind.domain.logprocessor.service.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnErrorEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis Streams Circuit Breaker 서비스
 *
 * <p>Redis 연결 실패 시 Circuit Breaker와 Exponential Backoff를 적용하여 안정적인 복구 메커니즘 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStreamCircuitBreakerService {

    private final RedisTemplate<String, String> redisTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final MeterRegistry meterRegistry;

    private CircuitBreaker circuitBreaker;
    private Retry retry;

    @PostConstruct
    public void init() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisStreamConsumer");
        retry = retryRegistry.retry("redisStreamConsumer");

        // Circuit Breaker 이벤트 리스너 등록
        circuitBreaker
                .getEventPublisher()
                .onStateTransition(this::handleStateTransition)
                .onError(this::handleError);

        // Retry 이벤트 리스너 등록
        retry.getEventPublisher().onRetry(this::handleRetry);
    }

    /**
     * Redis Stream 메시지 읽기 (Circuit Breaker + Retry 적용)
     *
     * @param consumer Consumer 정보
     * @param options Stream 읽기 옵션
     * @param offset Stream Offset
     * @return 읽어온 메시지 목록 (실패 시 빈 리스트)
     */
    public List<MapRecord<String, String, String>> readMessages(
            Consumer consumer, StreamReadOptions options, StreamOffset<String> offset) {

        Supplier<List<MapRecord<String, String, String>>> supplier =
                () -> executeRedisRead(consumer, options, offset);

        // Retry -> CircuitBreaker 순서로 데코레이션
        Supplier<List<MapRecord<String, String, String>>> decoratedSupplier =
                Decorators.ofSupplier(supplier)
                        .withRetry(retry)
                        .withCircuitBreaker(circuitBreaker)
                        .decorate();

        try {
            return decoratedSupplier.get();
        } catch (CallNotPermittedException e) {
            log.warn("[CircuitBreaker] Circuit is OPEN. Skipping Redis poll.");
            return List.of();
        } catch (Exception e) {
            log.error("[CircuitBreaker] Failed to read Redis Stream", e);
            return List.of();
        }
    }

    /**
     * Redis Stream 실제 읽기 실행
     *
     * @param consumer Consumer 정보
     * @param options Stream 읽기 옵션
     * @param offset Stream Offset
     * @return 읽어온 메시지 목록
     * @throws ClassCastException RedisTemplate 제네릭이 일치하지 않을 경우
     */
    @SuppressWarnings("unchecked")
    private List<MapRecord<String, String, String>> executeRedisRead(
            Consumer consumer, StreamReadOptions options, StreamOffset<String> offset) {

        try {
            List<?> rawMessages = redisTemplate.opsForStream().read(consumer, options, offset);

            if (rawMessages == null || rawMessages.isEmpty()) {
                return List.of();
            }

            // 타입 안전성 검증: 첫 번째 요소가 MapRecord인지 확인
            Object firstElement = rawMessages.get(0);
            if (!(firstElement instanceof MapRecord)) {
                log.error(
                        "[Type Safety] Unexpected type from Redis Stream: {}. Expected MapRecord.",
                        firstElement.getClass().getName());
                return List.of();
            }

            return (List<MapRecord<String, String, String>>) (List<?>) rawMessages;
        } catch (ClassCastException e) {
            log.error(
                    "[Type Safety] ClassCastException during Redis Stream read. "
                            + "Check RedisTemplate serializer configuration.",
                    e);
            throw e; // Circuit Breaker가 감지하도록 예외 전파
        }
    }

    /**
     * Circuit Breaker 상태 전환 이벤트 처리
     *
     * @param event 상태 전환 이벤트
     */
    private void handleStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        log.warn(
                "[CircuitBreaker] State transition: {} -> {}",
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState());

        // 커스텀 메트릭 기록
        meterRegistry
                .counter(
                        "redis.circuit.state.transitions",
                        "from",
                        event.getStateTransition().getFromState().name(),
                        "to",
                        event.getStateTransition().getToState().name())
                .increment();
    }

    /**
     * Circuit Breaker 에러 이벤트 처리
     *
     * @param event 에러 이벤트
     */
    private void handleError(CircuitBreakerOnErrorEvent event) {
        log.error("[CircuitBreaker] Error recorded: {}", event.getThrowable().getMessage());
    }

    /**
     * Retry 이벤트 처리
     *
     * @param event 재시도 이벤트
     */
    private void handleRetry(RetryOnRetryEvent event) {
        log.warn(
                "[Retry] Attempt {}/{}: {}",
                event.getNumberOfRetryAttempts(),
                retry.getRetryConfig().getMaxAttempts(),
                event.getLastThrowable().getMessage());
    }

    /**
     * Circuit Breaker 현재 상태 조회
     *
     * @return Circuit Breaker 상태
     */
    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }
}

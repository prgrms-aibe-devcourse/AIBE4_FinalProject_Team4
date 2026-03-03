package kr.java.documind.domain.logprocessor.service.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisStreamCircuitBreakerService 단위 테스트")
class RedisStreamCircuitBreakerServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;

    @Mock private StreamOperations<String, Object, Object> streamOperations;

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;
    private MeterRegistry meterRegistry;
    private RedisStreamCircuitBreakerService service;

    @BeforeEach
    void setUp() {
        // Circuit Breaker 설정 (테스트용)
        CircuitBreakerConfig circuitBreakerConfig =
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(3)
                        .slidingWindowSize(10)
                        .waitDurationInOpenState(Duration.ofSeconds(1))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .recordException(e -> e instanceof RedisConnectionFailureException)
                        .build();

        circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);

        // Retry 설정 (테스트용)
        RetryConfig retryConfig =
                RetryConfig.custom()
                        .maxAttempts(3)
                        .waitDuration(Duration.ofMillis(100))
                        .retryExceptions(RedisConnectionFailureException.class)
                        .build();

        retryRegistry = RetryRegistry.of(retryConfig);

        // Meter Registry
        meterRegistry = new SimpleMeterRegistry();

        // Service 생성
        service =
                new RedisStreamCircuitBreakerService(
                        redisTemplate, circuitBreakerRegistry, retryRegistry, meterRegistry);

        // RedisTemplate Mock 설정 (lenient 모드로 모든 테스트에서 사용 가능)
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        // Service 초기화
        service.init();
    }

    private void setupRedisSuccess() {
        when(streamOperations.read(
                        any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of());
    }

    private void setupRedisFailure() {
        when(streamOperations.read(
                        any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenThrow(new RedisConnectionFailureException("Connection lost"));
    }

    @Test
    @DisplayName("정상 동작: Redis 메시지 읽기 성공")
    void readMessages_Success() {
        // Given
        setupRedisSuccess();

        Consumer consumer = Consumer.from("test-group", "test-consumer");
        StreamReadOptions options = StreamReadOptions.empty();
        StreamOffset<String> offset = StreamOffset.latest("test-stream");

        // When
        List<MapRecord<String, String, String>> result =
                service.readMessages(consumer, options, offset);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Circuit Breaker: 3회 실패 후 OPEN 상태로 전환")
    void circuitBreakerOpensAfterThreeFailures() {
        // Given: Redis 연결 실패 시뮬레이션
        setupRedisFailure();

        Consumer consumer = Consumer.from("test-group", "test-consumer");
        StreamReadOptions options = StreamReadOptions.empty();
        StreamOffset<String> offset = StreamOffset.latest("test-stream");

        CircuitBreaker circuitBreaker =
                circuitBreakerRegistry.circuitBreaker("redisStreamConsumer");

        // When: 3회 연속 호출
        for (int i = 0; i < 3; i++) {
            service.readMessages(consumer, options, offset);
        }

        // Then: Circuit OPEN 상태
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // And: 4번째 호출은 빈 리스트 반환 (Circuit이 OPEN이므로 Redis 호출 없음)
        List<MapRecord<String, String, String>> result =
                service.readMessages(consumer, options, offset);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Circuit Breaker: OPEN 상태에서 빈 리스트 반환")
    void circuitBreakerReturnsEmptyListWhenOpen() {
        // Given: Circuit을 OPEN 상태로 만듦
        setupRedisFailure();

        Consumer consumer = Consumer.from("test-group", "test-consumer");
        StreamReadOptions options = StreamReadOptions.empty();
        StreamOffset<String> offset = StreamOffset.latest("test-stream");

        // 3회 실패시켜 OPEN 상태로 전환
        for (int i = 0; i < 3; i++) {
            service.readMessages(consumer, options, offset);
        }

        CircuitBreaker circuitBreaker =
                circuitBreakerRegistry.circuitBreaker("redisStreamConsumer");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // When: OPEN 상태에서 호출
        List<MapRecord<String, String, String>> result =
                service.readMessages(consumer, options, offset);

        // Then: 빈 리스트 반환
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Circuit Breaker 상태 조회")
    void getCircuitBreakerState() {
        // Given
        CircuitBreaker circuitBreaker =
                circuitBreakerRegistry.circuitBreaker("redisStreamConsumer");

        // When
        CircuitBreaker.State state = service.getCircuitBreakerState();

        // Then: 초기 상태는 CLOSED
        assertThat(state).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(state).isEqualTo(circuitBreaker.getState());
    }

    @Test
    @DisplayName("Retry: 연속 실패 후 Circuit OPEN")
    void retryAndCircuitBreakerWorkTogether() {
        // Given
        setupRedisFailure();

        Consumer consumer = Consumer.from("test-group", "test-consumer");
        StreamReadOptions options = StreamReadOptions.empty();
        StreamOffset<String> offset = StreamOffset.latest("test-stream");

        CircuitBreaker circuitBreaker =
                circuitBreakerRegistry.circuitBreaker("redisStreamConsumer");

        // When: 계속 실패
        for (int i = 0; i < 5; i++) {
            service.readMessages(consumer, options, offset);
        }

        // Then: Circuit이 OPEN 되어야 함
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}

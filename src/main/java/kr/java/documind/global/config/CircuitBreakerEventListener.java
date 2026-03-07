package kr.java.documind.global.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnErrorEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Circuit Breaker 이벤트 리스너
 *
 * <p>Circuit Breaker 상태 전환 및 에러 이벤트를 감지하여 로깅 및 알림 처리
 *
 * <p>주요 이벤트: - CLOSED → OPEN: 장애 발생 (긴급 알림) - OPEN → HALF_OPEN: 복구 시도 중 - HALF_OPEN → CLOSED: 복구 완료
 * (정상화 알림) - ERROR: 개별 에러 발생 로그
 */
@Slf4j
@Configuration
public class CircuitBreakerEventListener {

    /**
     * Circuit Breaker Registry에 이벤트 리스너 등록
     *
     * <p>모든 Circuit Breaker에 대해 이벤트 감지
     *
     * @return RegistryEventConsumer
     */
    @Bean
    public RegistryEventConsumer<CircuitBreaker> customCircuitBreakerRegistryEventConsumer() {
        return new RegistryEventConsumer<CircuitBreaker>() {

            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
                log.info("✅ Circuit Breaker registered: {}", circuitBreaker.getName());

                // 상태 전환 이벤트 리스너 등록
                circuitBreaker
                        .getEventPublisher()
                        .onStateTransition(
                                event -> {
                                    handleStateTransition(event);
                                });

                // 에러 이벤트 리스너 등록
                circuitBreaker
                        .getEventPublisher()
                        .onError(
                                event -> {
                                    handleError(event);
                                });
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {
                log.info(
                        "Circuit Breaker removed: {}",
                        entryRemoveEvent.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(
                    EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
                log.info(
                        "Circuit Breaker replaced: {}", entryReplacedEvent.getNewEntry().getName());
            }
        };
    }

    /**
     * Circuit Breaker 상태 전환 처리
     *
     * <p>CLOSED → OPEN: 장애 발생 (실패율 50% 초과) OPEN → HALF_OPEN: 복구 시도 중 HALF_OPEN → CLOSED: 복구 완료
     * HALF_OPEN → OPEN: 복구 실패
     *
     * @param event 상태 전환 이벤트
     */
    private void handleStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        String circuitBreakerName = event.getCircuitBreakerName();
        CircuitBreaker.State fromState = event.getStateTransition().getFromState();
        CircuitBreaker.State toState = event.getStateTransition().getToState();

        log.warn(
                "🔄 Circuit Breaker [{}] state transition: {} → {}",
                circuitBreakerName,
                fromState,
                toState);

        // CLOSED → OPEN: 긴급 알림
        if (fromState == CircuitBreaker.State.CLOSED && toState == CircuitBreaker.State.OPEN) {
            handleCircuitOpen(circuitBreakerName);
        }

        // OPEN → HALF_OPEN: 복구 시도 로그
        if (fromState == CircuitBreaker.State.OPEN && toState == CircuitBreaker.State.HALF_OPEN) {
            log.info("🔧 Circuit Breaker [{}] attempting recovery...", circuitBreakerName);
        }

        // HALF_OPEN → CLOSED: 복구 완료 알림
        if (fromState == CircuitBreaker.State.HALF_OPEN && toState == CircuitBreaker.State.CLOSED) {
            handleCircuitClosed(circuitBreakerName);
        }

        // HALF_OPEN → OPEN: 복구 실패
        if (fromState == CircuitBreaker.State.HALF_OPEN && toState == CircuitBreaker.State.OPEN) {
            log.error(
                    "❌ Circuit Breaker [{}] recovery failed. Circuit remains OPEN.",
                    circuitBreakerName);
        }
    }

    /**
     * Circuit OPEN 상태 처리 (긴급 알림)
     *
     * <p>Redis 장애 등으로 회로가 차단된 상태 → 즉시 알림 발송
     *
     * @param circuitBreakerName Circuit Breaker 이름
     */
    private void handleCircuitOpen(String circuitBreakerName) {
        log.error(
                """
                🚨🚨🚨 [CRITICAL] Circuit Breaker OPEN 🚨🚨🚨
                Circuit: {}
                원인: 실패율 50% 초과
                영향: {} 요청이 즉시 실패 처리됨
                복구: 10초 후 자동으로 재시도 (HALF_OPEN)

                ⚠️ 긴급 조치 필요:
                1. Redis 서버 상태 확인
                2. 네트워크 연결 확인
                3. 로그 확인 및 원인 파악
                """,
                circuitBreakerName, circuitBreakerName);

        // TODO: 긴급 알림 시스템 연동
        // sendSlackAlert(circuitBreakerName, "OPEN");
        // sendEmailAlert(circuitBreakerName, "CRITICAL");
        // triggerPagerDuty(circuitBreakerName);
    }

    /**
     * Circuit CLOSED 상태 처리 (복구 완료)
     *
     * <p>장애 복구 완료 → 정상화 알림
     *
     * @param circuitBreakerName Circuit Breaker 이름
     */
    private void handleCircuitClosed(String circuitBreakerName) {
        log.info(
                """
                ✅✅✅ [RECOVERY] Circuit Breaker CLOSED ✅✅✅
                Circuit: {}
                상태: 정상 복구 완료
                영향: 모든 요청이 정상 처리됨
                """,
                circuitBreakerName);

        // TODO: 복구 알림 시스템 연동
        // sendSlackAlert(circuitBreakerName, "CLOSED");
        // sendEmailAlert(circuitBreakerName, "RECOVERY");
    }

    /**
     * 개별 에러 이벤트 처리
     *
     * <p>Circuit Breaker를 통과한 요청이 실패한 경우 로깅
     *
     * @param event 에러 이벤트
     */
    private void handleError(CircuitBreakerOnErrorEvent event) {
        String circuitBreakerName = event.getCircuitBreakerName();
        Throwable throwable = event.getThrowable();

        log.error(
                "❌ Circuit Breaker [{}] error occurred: {}",
                circuitBreakerName,
                throwable.getMessage());

        // 상세 에러 로그 (디버그 레벨)
        log.debug("Circuit Breaker [{}] error details: ", circuitBreakerName, throwable);
    }
}

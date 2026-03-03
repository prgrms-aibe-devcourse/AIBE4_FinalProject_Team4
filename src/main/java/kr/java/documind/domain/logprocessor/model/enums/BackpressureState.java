package kr.java.documind.domain.logprocessor.model.enums;

/**
 * Backpressure 상태를 나타내는 Enum
 *
 * <p>시스템 부하에 따라 처리 속도를 조절하기 위한 상태 관리
 */
public enum BackpressureState {
    NORMAL,   // 정상 상태
    WARN,     // 경고 상태 (지연 시간이 임계값 초과)
    CRITICAL  // 심각 상태 (지연 시간이 위험 수준)
}

package kr.java.documind.domain.issue.model.enums;

/**
 * 핑거프린트 생성 품질 등급
 *
 * <p>스택트레이스 및 에러 메시지 가용성에 따라 핑거프린트의 정확도를 나타냄
 */
public enum FingerprintQuality {
    /**
     * 전체 스택트레이스 기반 (가장 높은 정확도)
     *
     * <p>자동 그룹핑 가능
     */
    HIGH,

    /**
     * 부분 스택트레이스 기반 (높은 정확도)
     *
     * <p>자동 그룹핑 가능
     */
    MEDIUM,

    /**
     * 예외 타입 + 메시지 기반 (낮은 정확도)
     *
     * <p>수동 검토 필요
     */
    LOW,

    /**
     * 메시지만 기반 (매우 낮은 정확도)
     *
     * <p>수동 검토 필수
     */
    VERY_LOW,

    /**
     * 폴백 전략 (최소 정확도)
     *
     * <p>수동 검토 필수
     */
    FALLBACK;

    /**
     * 수동 검토가 필요한 품질인지 확인
     *
     * @return LOW, VERY_LOW, FALLBACK인 경우 true
     */
    public boolean requiresReview() {
        return this == LOW || this == VERY_LOW || this == FALLBACK;
    }
}

package kr.java.documind.domain.issue.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 이슈 심각도(Severity)를 정의하는 Enum
 *
 * <p>가중치 기반 점수 계산 알고리즘에 따라 자동 판별된 심각도 등급
 *
 * <p>점수 기준:
 *
 * <ul>
 *   <li>CRITICAL: 90-100점 (긴급 대응 필요)
 *   <li>HIGH: 60-89점 (높은 우선순위)
 *   <li>MEDIUM: 30-59점 (보통 우선순위)
 *   <li>LOW: 0-29점 (낮은 우선순위)
 * </ul>
 */
public enum IssueSeverity {
    CRITICAL("CRITICAL", 90, 100),
    HIGH("HIGH", 60, 89),
    MEDIUM("MEDIUM", 30, 59),
    LOW("LOW", 0, 29);

    private final String value;
    private final int minScore;
    private final int maxScore;

    IssueSeverity(String value, int minScore, int maxScore) {
        this.value = value;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    /**
     * 점수에 따라 심각도 등급 자동 결정
     *
     * @param score 계산된 점수 (0-100)
     * @return 해당 점수에 맞는 IssueSeverity
     * @throws IllegalArgumentException 점수가 0-100 범위를 벗어난 경우
     */
    public static IssueSeverity fromScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(
                    "Score must be between 0 and 100, but was: " + score);
        }

        for (IssueSeverity severity : values()) {
            if (score >= severity.minScore && score <= severity.maxScore) {
                return severity;
            }
        }

        // 이론적으로 도달 불가능 (0-100 범위 전체 커버)
        throw new IllegalStateException("Failed to determine severity for score: " + score);
    }

    /**
     * Enum을 JSON으로 직렬화할 때 사용할 값
     *
     * @return 심각도 문자열 (대문자)
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * JSON 역직렬화 및 String → Enum 변환 (대소문자 무시)
     *
     * <p>인식하지 못한 값은 MEDIUM으로 fallback (예외 발생하지 않음)
     *
     * <p>미래 버전/레거시 데이터 호환성 유지
     *
     * @param value 심각도 문자열
     * @return IssueSeverity enum
     */
    @JsonCreator
    public static IssueSeverity fromString(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM; // 기본값
        }

        for (IssueSeverity severity : values()) {
            if (severity.value.equalsIgnoreCase(value.trim())) {
                return severity;
            }
        }

        // 인식하지 못한 값은 MEDIUM으로 fallback (예외 발생하지 않음)
        // 미래 버전/레거시 데이터 호환성 유지
        return MEDIUM;
    }

    /**
     * 최소 점수 반환
     *
     * @return 최소 점수
     */
    public int getMinScore() {
        return minScore;
    }

    /**
     * 최대 점수 반환
     *
     * @return 최대 점수
     */
    public int getMaxScore() {
        return maxScore;
    }

    /**
     * 화면 표시용 레이블 (P1, P2, P3, P4 형식)
     *
     * @return 표시 이름
     */
    public String getDisplayLabel() {
        return switch (this) {
            case CRITICAL -> "P1 긴급";
            case HIGH -> "P2 높음";
            case MEDIUM -> "P3 보통";
            case LOW -> "P4 낮음";
        };
    }

    /**
     * DB에 저장할 때 사용 (VARCHAR 컬럼)
     *
     * @return 심각도 문자열 (대문자)
     */
    @Override
    public String toString() {
        return value;
    }
}

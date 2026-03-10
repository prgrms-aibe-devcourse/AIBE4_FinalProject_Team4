package kr.java.documind.domain.issue.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 이슈 타입
 *
 * <p>이슈의 근본적인 원인 분류
 */
public enum IssueType {
    BUG("BUG", "버그"),
    CRASH("CRASH", "크래시"),
    PERFORMANCE("PERFORMANCE", "성능"),
    NETWORK("NETWORK", "네트워크"),
    DATA_INCONSISTENCY("DATA_INCONSISTENCY", "데이터 불일치"),
    SECURITY("SECURITY", "보안"),
    PAYMENT("PAYMENT", "결제"),
    BALANCE("BALANCE", "밸런스"),
    UX("UX", "사용자 경험"),
    DEPENDENCY("DEPENDENCY", "의존성"),
    CONFIGURATION("CONFIGURATION", "설정"),
    UNKNOWN("UNKNOWN", "알 수 없음");

    private final String value;
    private final String description;

    IssueType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * JSON 역직렬화 및 String → Enum 변환 (대소문자 무시)
     *
     * <p>인식하지 못한 값은 UNKNOWN으로 fallback (예외 발생하지 않음)
     *
     * <p>미래 버전/레거시 데이터 호환성 유지
     *
     * @param value 이슈 타입 문자열
     * @return IssueType enum
     */
    @JsonCreator
    public static IssueType fromString(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        for (IssueType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }

        // 인식하지 못한 값은 UNKNOWN으로 fallback (예외 발생하지 않음)
        // 미래 버전/레거시 데이터 호환성 유지
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return value;
    }
}

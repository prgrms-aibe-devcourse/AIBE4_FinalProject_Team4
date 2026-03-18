package kr.java.documind.domain.issue.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.extern.slf4j.Slf4j;

/**
 * 이슈 우선순위(Priority)를 정의하는 Enum
 *
 * <p>비즈니스 관점에서 사용자가 수동으로 설정하는 우선순위
 *
 * <ul>
 *   <li>P1: 긴급 (즉시 처리 필요)
 *   <li>P2: 높음 (빠른 시일 내 처리)
 *   <li>P3: 보통 (일반적인 처리)
 *   <li>P4: 낮음 (여유 있을 때 처리)
 * </ul>
 */
@Slf4j
public enum IssuePriority {
    P1("P1", "긴급"),
    P2("P2", "높음"),
    P3("P3", "보통"),
    P4("P4", "낮음");

    private final String value;
    private final String displayName;

    IssuePriority(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    /**
     * Enum을 JSON으로 직렬화할 때 사용할 값
     *
     * @return 우선순위 문자열 (P1, P2, P3, P4)
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 화면 표시용 레이블
     *
     * @return 표시 이름 (긴급, 높음, 보통, 낮음)
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * JSON 역직렬화 및 String → Enum 변환 (대소문자 무시)
     *
     * <p>인식하지 못한 값은 P3으로 fallback (예외 발생하지 않음)
     *
     * @param value 우선순위 문자열
     * @return IssuePriority enum
     */
    @JsonCreator
    public static IssuePriority fromString(String value) {
        if (value == null || value.isBlank()) {
            return P3; // 기본값
        }

        for (IssuePriority priority : values()) {
            if (priority.value.equalsIgnoreCase(value.trim())) {
                return priority;
            }
        }

        // 인식하지 못한 값은 P3으로 fallback
        log.warn("Unknown priority value: {}, defaulting to P3", value);
        return P3;
    }

    /**
     * DB에 저장할 때 사용 (VARCHAR 컬럼)
     *
     * @return 우선순위 문자열 (P1, P2, P3, P4)
     */
    @Override
    public String toString() {
        return value;
    }
}

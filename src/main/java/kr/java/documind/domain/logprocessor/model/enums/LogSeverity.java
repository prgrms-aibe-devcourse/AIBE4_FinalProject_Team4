package kr.java.documind.domain.logprocessor.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 로그 심각도(Severity)를 정의하는 Enum
 *
 * <p>시스템 진단 및 디버깅을 위한 로그 레벨 분류
 */
public enum LogSeverity {
    TRACE("TRACE"),
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),
    FATAL("FATAL");

    private final String value;

    LogSeverity(String value) {
        this.value = value;
    }

    /**
     * Enum을 JSON으로 직렬화할 때 사용할 값
     *
     * @return 로그 심각도 문자열 (대문자)
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * JSON 역직렬화 및 String → Enum 변환 (대소문자 무시)
     *
     * @param value 로그 심각도 문자열
     * @return LogSeverity enum
     * @throws IllegalArgumentException 지원하지 않는 심각도인 경우
     */
    @JsonCreator
    public static LogSeverity fromString(String value) {
        if (value == null || value.isBlank()) {
            return INFO; // 기본값
        }

        for (LogSeverity severity : LogSeverity.values()) {
            if (severity.value.equalsIgnoreCase(value.trim())) {
                return severity;
            }
        }

        throw new IllegalArgumentException(
                "Unknown log severity: '"
                        + value
                        + "'. Supported values: TRACE, DEBUG, INFO, WARN, ERROR, FATAL");
    }

    /**
     * DB에 저장할 때 사용 (VARCHAR 컬럼)
     *
     * @return 로그 심각도 문자열 (대문자)
     */
    @Override
    public String toString() {
        return value;
    }
}

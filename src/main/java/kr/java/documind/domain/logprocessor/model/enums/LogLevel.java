package kr.java.documind.domain.logprocessor.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 로그 레벨을 정의하는 Enum
 *
 * <p>OpenTelemetry Severity Number 스펙과 호환 가능하도록 설계
 */
public enum LogLevel {
    TRACE("TRACE"),
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),
    FATAL("FATAL");

    private final String value;

    LogLevel(String value) {
        this.value = value;
    }

    /**
     * Enum을 JSON으로 직렬화할 때 사용할 값
     *
     * @return 로그 레벨 문자열 (대문자)
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * JSON 역직렬화 및 String → Enum 변환 (대소문자 무시)
     *
     * @param value 로그 레벨 문자열
     * @return LogLevel enum
     * @throws IllegalArgumentException 지원하지 않는 로그 레벨인 경우
     */
    @JsonCreator
    public static LogLevel fromString(String value) {
        if (value == null || value.isBlank()) {
            return INFO; // 기본값
        }

        for (LogLevel level : LogLevel.values()) {
            if (level.value.equalsIgnoreCase(value.trim())) {
                return level;
            }
        }

        throw new IllegalArgumentException(
                "Unknown log level: '"
                        + value
                        + "'. Supported values: TRACE, DEBUG, INFO, WARN, ERROR, FATAL");
    }

    /**
     * DB에 저장할 때 사용 (VARCHAR 컬럼)
     *
     * @return 로그 레벨 문자열 (대문자)
     */
    @Override
    public String toString() {
        return value;
    }
}

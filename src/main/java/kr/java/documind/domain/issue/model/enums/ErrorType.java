package kr.java.documind.domain.issue.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 에러 타입
 *
 * <p>예외/에러의 구체적인 종류
 */
public enum ErrorType {
    NULL_POINTER("NULL_POINTER", "NullPointerException"),
    INDEX_OUT_OF_BOUNDS("INDEX_OUT_OF_BOUNDS", "IndexOutOfBoundsException"),
    ILLEGAL_ARGUMENT("ILLEGAL_ARGUMENT", "IllegalArgumentException"),
    ILLEGAL_STATE("ILLEGAL_STATE", "IllegalStateException"),
    TIMEOUT("TIMEOUT", "TimeoutException"),
    IO("IO", "IOException"),
    NETWORK("NETWORK", "네트워크 오류"),
    DATABASE("DATABASE", "데이터베이스 오류"),
    DEADLOCK("DEADLOCK", "교착 상태"),
    AUTHENTICATION("AUTHENTICATION", "인증 실패"),
    AUTHORIZATION("AUTHORIZATION", "권한 부족"),
    SERIALIZATION("SERIALIZATION", "직렬화 오류"),
    OUT_OF_MEMORY("OUT_OF_MEMORY", "메모리 부족"),
    STACK_OVERFLOW("STACK_OVERFLOW", "스택 오버플로"),
    ARITHMETIC("ARITHMETIC", "산술 연산 오류"),
    UNSUPPORTED_OPERATION("UNSUPPORTED_OPERATION", "지원하지 않는 작업"),
    CONCURRENCY("CONCURRENCY", "동시성 문제"),
    DEPENDENCY_FAILURE("DEPENDENCY_FAILURE", "외부 의존성 실패"),
    UNKNOWN("UNKNOWN", "알 수 없는 오류");

    private final String value;
    private final String description;

    ErrorType(String value, String description) {
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
     * 예외 클래스명에서 ErrorType 추론
     *
     * @param exceptionClassName 예외 클래스명 (예: "NullPointerException")
     * @return 매칭되는 ErrorType (없으면 UNKNOWN)
     */
    public static ErrorType fromExceptionClassName(String exceptionClassName) {
        if (exceptionClassName == null || exceptionClassName.isBlank()) {
            return UNKNOWN;
        }

        String className = exceptionClassName.toLowerCase();

        if (className.contains("nullpointer")) return NULL_POINTER;
        if (className.contains("indexoutofbounds")) return INDEX_OUT_OF_BOUNDS;
        if (className.contains("illegalargument")) return ILLEGAL_ARGUMENT;
        if (className.contains("illegalstate")) return ILLEGAL_STATE;
        if (className.contains("timeout")) return TIMEOUT;
        if (className.contains("ioexception")) return IO;
        if (className.contains("network")) return NETWORK;
        if (className.contains("database") || className.contains("sql")) return DATABASE;
        if (className.contains("deadlock")) return DEADLOCK;
        if (className.contains("authentication")) return AUTHENTICATION;
        if (className.contains("authorization") || className.contains("accessdenied"))
            return AUTHORIZATION;
        if (className.contains("serialization")) return SERIALIZATION;
        if (className.contains("outofmemory")) return OUT_OF_MEMORY;
        if (className.contains("stackoverflow")) return STACK_OVERFLOW;
        if (className.contains("arithmetic")) return ARITHMETIC;
        if (className.contains("unsupportedoperation")) return UNSUPPORTED_OPERATION;
        if (className.contains("concurrency") || className.contains("concurrent"))
            return CONCURRENCY;

        return UNKNOWN;
    }

    @JsonCreator
    public static ErrorType fromString(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        for (ErrorType type : values()) {
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

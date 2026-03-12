package kr.java.documind.domain.issue.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 이슈 상태
 *
 * <p>RECOMMENDED (추천) → TODO (승인) → IN_PROGRESS → RESOLVED
 *
 * <p>RECOMMENDED → REJECTED (거부)
 */
public enum IssueStatus {
    RECOMMENDED("RECOMMENDED", "추천 대기"),
    TODO("TODO", "할 일"),
    IN_PROGRESS("IN_PROGRESS", "진행 중"),
    RESOLVED("RESOLVED", "해결됨"),
    REJECTED("REJECTED", "거부됨");

    private final String value;
    private final String description;

    IssueStatus(String value, String description) {
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

    @JsonCreator
    public static IssueStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return TODO;
        }

        for (IssueStatus status : values()) {
            if (status.value.equalsIgnoreCase(value.trim())) {
                return status;
            }
        }

        throw new IllegalArgumentException(
                "Unknown issue status: '"
                        + value
                        + "'. Supported values: RECOMMENDED, TODO, IN_PROGRESS, RESOLVED, REJECTED");
    }

    @Override
    public String toString() {
        return value;
    }
}

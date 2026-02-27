package kr.java.documind.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final String message;
    private final Object details;

    private ErrorResponse(String message, Object details) {
        this.message = message;
        this.details = details;
    }

    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, null);
    }

    public static ErrorResponse withDetails(String message, Object details) {
        return new ErrorResponse(message, details);
    }

    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldError {

        private final String field;
        private final String reason;

        private FieldError(String field, String reason) {
            this.field = field;
            this.reason = reason;
        }

        public static FieldError of(String field, String reason) {
            return new FieldError(field, reason);
        }
    }
}

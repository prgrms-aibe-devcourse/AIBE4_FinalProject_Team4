package kr.java.documind.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final Object meta;
    private final ErrorResponse error;

    private ApiResponse(boolean success, String message, T data, Object meta, ErrorResponse error) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.meta = meta;
        this.error = error;
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, null, null, null, null);
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, null, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, null, null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, null);
    }

    public static <T, M> ApiResponse<T> success(T data, M meta) {
        return new ApiResponse<>(true, null, data, meta, null);
    }

    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, null, null, error);
    }
}

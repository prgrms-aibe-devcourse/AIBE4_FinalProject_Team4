package kr.java.documind.global.exception;

import org.springframework.http.HttpStatus;

public class StorageException extends BusinessException {

    public StorageException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public StorageException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}

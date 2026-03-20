package kr.java.documind.global.exception;

import org.springframework.http.HttpStatus;

public class NotificationNotFoundException extends BusinessException {

    public NotificationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다.");
    }
}

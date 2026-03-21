package kr.java.documind.domain.dashboard.exception;

import kr.java.documind.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DashboardLimitExceededException extends BusinessException {

    public DashboardLimitExceededException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}

package kr.java.documind.global.exception;

import java.lang.reflect.Method;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

@Slf4j
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error("[Async Error] 메서드: {}, 파라미터: {}", method.getName(), Arrays.toString(params));

//        if (ex instanceof CustomException ce) {
//            log.error("비즈니스 예외: {}", ce.getMessage());
//        } else {
//            log.error("예측 불가 예외: {}", ex.getMessage(), ex);
//        }
    }
}

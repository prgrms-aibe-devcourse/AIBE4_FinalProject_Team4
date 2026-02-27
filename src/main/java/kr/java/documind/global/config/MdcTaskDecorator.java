package kr.java.documind.global.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        // 1. 메인 스레드의 MDC 컨텍스트 맵을 복사
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                // 2. 비동기 스레드 실행 직전 MDC 값 주입
                if (contextMap != null) MDC.setContextMap(contextMap);
                runnable.run();
            } finally {
                // 3. [필수] 스레드 풀 재사용 시 이전 데이터 노출 방지
                MDC.clear();
            }
        };
    }
}

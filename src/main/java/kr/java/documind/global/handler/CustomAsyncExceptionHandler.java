package kr.java.documind.global.handler;

import java.lang.reflect.Method;
import java.util.Arrays;
import kr.java.documind.domain.patchnote.exception.DocumentEmbeddingEmptyException;
import kr.java.documind.domain.patchnote.exception.IssueInsufficientInfoException;
import kr.java.documind.domain.patchnote.exception.PendingItemUpsertFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

@Slf4j
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error("[Async Error] 메서드: {}, 파라미터: {}", method.getName(), Arrays.toString(params));

        if (ex instanceof IssueInsufficientInfoException) {
            // 정보 부족 이슈 — 이슈 담당자에게 경고 top-toast
            // TODO: 담당자 경고 top-toast 발송 연동
            log.warn("[Async] 이슈 정보 부족 — 담당자 경고 알림 대기: {}", ex.getMessage());

        } else if (ex instanceof DocumentEmbeddingEmptyException) {
            // 임베딩 완료 후 벡터 스토어 청크 없음 — 문서 업로드 담당자에게 경고 top-toast
            // TODO: 문서 업로드 담당자 경고 top-toast 발송 연동
            log.warn("[Async] 문서 임베딩 후 청크 없음 — 업로드 담당자 경고 알림 대기: {}", ex.getMessage());

        } else if (ex instanceof PendingItemUpsertFailedException) {
            // pending_item 최종 저장 3회 실패 (고아 벡터 정리 완료) — 관리자 알림 top-toast
            // TODO: 관리자 알림 top-toast 발송 연동
            log.error("[Async] pending_item 저장 실패 — 관리자 알림 대기: {}", ex.getMessage());

        } else {
            // vector 저장 실패 · 청킹·임베딩 등 예기치 않은 실패 — 관리자 알림 top-toast
            // TODO: 관리자 알림 top-toast 발송 연동
            log.error("[Async] 처리 중 예기치 않은 실패 — 관리자 알림 대기: {}", ex.getMessage(), ex);
        }
    }
}

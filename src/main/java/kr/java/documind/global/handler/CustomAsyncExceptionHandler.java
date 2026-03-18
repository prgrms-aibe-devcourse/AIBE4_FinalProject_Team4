package kr.java.documind.global.handler;

import java.lang.reflect.Method;
import java.util.Arrays;
import kr.java.documind.domain.patchnote.exception.IssueInsufficientInfoException;
import kr.java.documind.domain.patchnote.exception.PendingItemUpsertFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

/**
 * {@code @Async} 메서드에서 던져진 미처리 예외를 처리한다.
 *
 * <p>예외 타입별로 알림 대상과 토스트 종류가 다르므로 instanceof 분기로 구분한다.
 *
 * <table>
 *   <tr><th>예외 타입</th><th>알림 대상</th><th>프론트엔드 처리</th></tr>
 *   <tr><td>{@link IssueInsufficientInfoException}</td><td>이슈 담당자</td><td>경고 top-toast</td></tr>
 *   <tr><td>{@link PendingItemUpsertFailedException}</td><td>관리자</td><td>관리자 알림 top-toast</td></tr>
 *   <tr><td>기타 {@link Exception}</td><td>관리자</td><td>관리자 알림 top-toast</td></tr>
 * </table>
 */
@Slf4j
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error("[Async Error] 메서드: {}, 파라미터: {}", method.getName(), Arrays.toString(params));

        if (ex instanceof IssueInsufficientInfoException) {
            // 정보 부족 이슈 — 이슈 담당자에게 경고 top-toast
            // TODO: 담당자 경고 top-toast 발송 연동
            log.warn("[Async] 이슈 정보 부족 — 담당자 경고 알림 대기: {}", ex.getMessage());

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

package kr.java.documind.domain.patchnote.event;

import kr.java.documind.domain.archive.document.event.DocumentVectorDeleteEvent;
import kr.java.documind.domain.patchnote.service.PendingItemRollbackService;
import kr.java.documind.global.enums.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 문서 삭제 시 patchnote 도메인의 pending_item을 정리한다.
 *
 * <ul>
 *   <li>PENDING / EXCLUDED → hard delete (원본 소스가 사라졌으므로 패치노트 생성 대상에서 제거)
 *   <li>COMPLETED → soft delete — {@code sourceDeleted} 플래그 처리 (이미 패치노트에 사용된 항목은 이력 보존)
 * </ul>
 *
 * <p>{@link DocumentVectorDeleteEvent}는 문서 물리 삭제 커밋 이후 발행된다.
 * 예외 발생 시 로그만 남기고 전파하지 않는다 — 벡터 삭제는 이미 완료되었으므로 pending_item 정리
 * 실패가 문서 삭제 성공을 취소하지 않아야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentPendingItemRollbackEventListener {

    private final PendingItemRollbackService pendingItemRollbackService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDocumentDeleted(DocumentVectorDeleteEvent event) {
        log.info(
                "[PatchNote] 문서 삭제 감지 — pending_item 정리 시작. sourceId: {}, projectId: {}",
                event.sourceId(),
                event.projectId());

        try {
            boolean hardDeleted =
                    pendingItemRollbackService.deleteForRollback(
                            event.projectId(), event.sourceId(), SourceType.DOCUMENT);

            if (hardDeleted) {
                log.info(
                        "[PatchNote] 문서 pending_item hard delete 완료. sourceId: {}",
                        event.sourceId());
            } else {
                log.info(
                        "[PatchNote] 문서 pending_item sourceDeleted 처리 완료 (COMPLETED 항목). sourceId: {}",
                        event.sourceId());
            }
        } catch (Exception e) {
            log.error(
                    "[PatchNote] 문서 pending_item 정리 실패 — 수동 확인 필요. sourceId: {}, projectId: {}",
                    event.sourceId(),
                    event.projectId(),
                    e);
        }
    }
}

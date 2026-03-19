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

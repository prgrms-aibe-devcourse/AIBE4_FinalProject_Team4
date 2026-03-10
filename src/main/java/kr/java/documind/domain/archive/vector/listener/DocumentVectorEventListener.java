package kr.java.documind.domain.archive.vector.listener;

import kr.java.documind.domain.archive.document.model.event.DocumentVectorCreateEvent;
import kr.java.documind.domain.archive.document.model.event.DocumentVectorDeleteEvent;
import kr.java.documind.domain.archive.document.model.event.DocumentVectorReplaceEvent;
import kr.java.documind.domain.archive.vector.service.EtlService;
import kr.java.documind.domain.archive.vector.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DocumentVectorEventListener {

    private final EtlService etlService;
    private final VectorStoreService vectorStoreService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVectorCreate(DocumentVectorCreateEvent event) {
        etlService.process(event.sourceId(), event.tempFilePath());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVectorReplace(DocumentVectorReplaceEvent event) {
        vectorStoreService.deleteBySourceId(event.sourceId());
        etlService.process(event.sourceId(), event.tempFilePath());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVectorDelete(DocumentVectorDeleteEvent event) {
        vectorStoreService.deleteBySourceId(event.sourceId());
    }
}

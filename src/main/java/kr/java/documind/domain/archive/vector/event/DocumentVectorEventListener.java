package kr.java.documind.domain.archive.vector.event;

import kr.java.documind.domain.archive.document.event.DocumentVectorCreateEvent;
import kr.java.documind.domain.archive.document.event.DocumentVectorDeleteEvent;
import kr.java.documind.domain.archive.document.event.DocumentVectorReplaceEvent;
import kr.java.documind.domain.archive.vector.infrastructure.VectorStoreManager;
import kr.java.documind.domain.archive.vector.service.EtlService;
import kr.java.documind.global.enums.SourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DocumentVectorEventListener {

    private final EtlService etlService;
    private final VectorStoreManager vectorStoreManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVectorCreate(DocumentVectorCreateEvent event) {
        etlService.process(event.projectId(), event.sourceId(), event.storedKey(), event.excludeFromPatchNote());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVectorReplace(DocumentVectorReplaceEvent event) {
        vectorStoreManager.deleteBySourceId(event.sourceId(), SourceType.DOCUMENT);
        etlService.process(event.projectId(), event.sourceId(), event.storedKey(), event.excludeFromPatchNote());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVectorDelete(DocumentVectorDeleteEvent event) {
        vectorStoreManager.deleteBySourceId(event.sourceId(), SourceType.DOCUMENT);
    }
}

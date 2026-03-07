package kr.java.documind.domain.archive.etl.listener;

import kr.java.documind.domain.archive.document.model.event.DocumentEtlEvent;
import kr.java.documind.domain.archive.document.model.event.DocumentVectorDeleteEvent;
import kr.java.documind.domain.archive.etl.service.DocumentEtlService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DocumentEtlListener {

    private final DocumentEtlService documentEtlService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DocumentEtlEvent event) {
        documentEtlService.process(event.sourceId(), event.tempFilePath());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DocumentVectorDeleteEvent event) {
        documentEtlService.deleteVectors(event.sourceId());
    }
}

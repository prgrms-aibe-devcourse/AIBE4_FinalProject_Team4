package kr.java.documind.domain.archive.document.event;

import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.vector.model.event.EmbeddingStatusEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EmbeddingStatusUpdateListener {

    private final DocumentMetadataManager documentMetadataManager;

    @EventListener
    @Transactional
    public void handle(EmbeddingStatusEvent event) {
        documentMetadataManager.updateEmbeddingStatusIfExists(event.sourceId(), event.status());
    }
}

package kr.java.documind.domain.archive.document.event;

import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.vector.event.EmbeddingStatusUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingStatusUpdateListener {

    private final DocumentMetadataManager documentMetadataManager;

    @EventListener
    @Transactional
    public void handle(EmbeddingStatusUpdateEvent event) {
        documentMetadataManager.updateEmbeddingStatusIfExists(event.sourceId(), event.status());
    }
}

package kr.java.documind.domain.archive.document.listener;

import kr.java.documind.domain.archive.document.model.repository.DocumentMetadataRepository;
import kr.java.documind.domain.archive.etl.model.event.EmbeddingStatusEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EmbeddingStatusListener {

    private final DocumentMetadataRepository documentMetadataRepository;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(EmbeddingStatusEvent event) {
        documentMetadataRepository
                .findById(event.sourceId())
                .ifPresent(m -> m.changeEmbeddingStatus(event.status()));
    }
}

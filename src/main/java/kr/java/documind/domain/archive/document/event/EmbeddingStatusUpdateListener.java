package kr.java.documind.domain.archive.document.event;

import java.time.Instant;
import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.vector.event.EmbeddingStatusEvent;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;
import kr.java.documind.domain.patchnote.event.DocumentEmbeddedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingStatusUpdateListener {

    private final DocumentMetadataManager documentMetadataManager;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    @Transactional
    public void handle(EmbeddingStatusEvent event) {
        documentMetadataManager.updateEmbeddingStatusIfExists(event.sourceId(), event.status());

        if (event.status() == EmbeddingStatus.SUCCESS) {
            publishDocumentEmbeddedEvent(event.sourceId());
        }
    }

    private void publishDocumentEmbeddedEvent(Long sourceId) {
        documentMetadataManager
                .findById(sourceId)
                .ifPresent(
                        metadata -> {
                            DocumentGroup group = metadata.getDocumentGroup();
                            boolean isNewDocument = metadata.getReuploadedAt() == null;

                            eventPublisher.publishEvent(
                                    new DocumentEmbeddedEvent(
                                            metadata.getId(),
                                            group.getProjectId(),
                                            metadata.getDocumentName(),
                                            group.getGroupName(),
                                            group.getCategory(),
                                            isNewDocument,
                                            Instant.now()));

                            log.debug(
                                    "[EmbeddingStatus] DocumentEmbeddedEvent 발행 - sourceId: {}, isNew: {}",
                                    sourceId,
                                    isNewDocument);
                        });
    }
}

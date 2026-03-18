package kr.java.documind.domain.archive.document.event;

import java.time.OffsetDateTime;
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
            publishDocumentEmbeddedEvent(event.sourceId(), event.excludeFromPatchNote());
        }
    }

    private void publishDocumentEmbeddedEvent(Long sourceId, boolean excludeFromPatchNote) {
        documentMetadataManager
                .findById(sourceId)
                .ifPresent(
                        metadata -> {
                            DocumentGroup group = metadata.getDocumentGroup();
                            boolean isNewDocument = metadata.getReuploadedAt() == null;

                            // 이 버전이 업로드된 시각: 재업로드면 reuploadedAt, 신규면 uploadedAt
                            OffsetDateTime sourceCreatedAt =
                                    metadata.getReuploadedAt() != null
                                            ? metadata.getReuploadedAt()
                                            : metadata.getUploadedAt();

                            eventPublisher.publishEvent(
                                    new DocumentEmbeddedEvent(
                                            metadata.getId(),
                                            group.getProjectId(),
                                            metadata.getDocumentName(),
                                            group.getGroupName(),
                                            group.getCategory(),
                                            isNewDocument,
                                            excludeFromPatchNote,
                                            sourceCreatedAt));

                            log.debug(
                                    "[EmbeddingStatus] DocumentEmbeddedEvent 발행 - sourceId: {}, isNew: {}, exclude: {}",
                                    sourceId,
                                    isNewDocument,
                                    excludeFromPatchNote);
                        });
    }
}

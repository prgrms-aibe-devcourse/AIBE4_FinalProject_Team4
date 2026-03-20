package kr.java.documind.domain.patchnote.infrastructure;

import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.vector.event.DocumentEmbeddedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentEmbeddedEventPublisher {

    private final DocumentMetadataManager documentMetadataManager;
    private final ApplicationEventPublisher eventPublisher;

    public void publishDocumentEmbeddedEvent(Long sourceId, boolean excludeFromPatchNote) {
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
                            group.getId(),
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

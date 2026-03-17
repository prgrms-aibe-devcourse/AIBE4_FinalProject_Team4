package kr.java.documind.domain.archive.document.infrastructure;

import java.util.UUID;
import kr.java.documind.domain.archive.document.event.DocumentVectorCreateEvent;
import kr.java.documind.domain.archive.document.event.DocumentVectorDeleteEvent;
import kr.java.documind.domain.archive.document.event.DocumentVectorReplaceEvent;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;
import kr.java.documind.global.enums.AllowedFileType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentVectorEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void createEvent(UUID projectId, DocumentMetadata documentMetadata) {
        if (isEmbeddable(documentMetadata.getExtension())) {
            documentMetadata.changeEmbeddingStatus(EmbeddingStatus.PENDING);
            eventPublisher.publishEvent(
                    new DocumentVectorCreateEvent(
                            projectId, documentMetadata.getId(), documentMetadata.getStoredKey()));
        }
    }

    public void replaceEvent(UUID projectId, DocumentMetadata documentMetadata) {
        if (isEmbeddable(documentMetadata.getExtension())) {
            documentMetadata.changeEmbeddingStatus(EmbeddingStatus.PENDING);
            eventPublisher.publishEvent(
                    new DocumentVectorReplaceEvent(
                            projectId, documentMetadata.getId(), documentMetadata.getStoredKey()));
        } else {
            documentMetadata.changeEmbeddingStatus(EmbeddingStatus.NONE);
            deleteEvent(documentMetadata.getId());
        }
    }

    public void deleteEvent(Long documentId) {
        eventPublisher.publishEvent(new DocumentVectorDeleteEvent(documentId));
    }

    private boolean isEmbeddable(String extension) {
        AllowedFileType fileType = AllowedFileType.fromExtension(extension);
        return fileType != null && fileType.isEmbeddable();
    }
}

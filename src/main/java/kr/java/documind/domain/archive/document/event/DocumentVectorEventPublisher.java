package kr.java.documind.domain.archive.document.event;

import java.util.UUID;
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

    public void createEvent(
            String publicId,
            UUID projectId,
            UUID memberId,
            DocumentMetadata documentMetadata,
            boolean excludeFromPatchNote) {
        if (isEmbeddable(documentMetadata.getExtension())) {
            documentMetadata.changeEmbeddingStatus(EmbeddingStatus.PENDING);
            eventPublisher.publishEvent(
                    new DocumentVectorCreateEvent(
                            publicId,
                            projectId,
                            memberId,
                            documentMetadata.getId(),
                            documentMetadata.getStoredKey(),
                            excludeFromPatchNote));
        }
    }

    public void retryEvent(
            String publicId, UUID projectId, UUID memberId, DocumentMetadata documentMetadata) {
        documentMetadata.changeEmbeddingStatus(EmbeddingStatus.PENDING);
        eventPublisher.publishEvent(
                new DocumentVectorCreateEvent(
                        publicId,
                        projectId,
                        memberId,
                        documentMetadata.getId(),
                        documentMetadata.getStoredKey(),
                        false));
    }

    public void replaceEvent(
            String publicId,
            UUID projectId,
            UUID memberId,
            DocumentMetadata documentMetadata,
            boolean excludeFromPatchNote) {
        if (isEmbeddable(documentMetadata.getExtension())) {
            documentMetadata.changeEmbeddingStatus(EmbeddingStatus.PENDING);
            eventPublisher.publishEvent(
                    new DocumentVectorReplaceEvent(
                            publicId,
                            projectId,
                            memberId,
                            documentMetadata.getId(),
                            documentMetadata.getStoredKey(),
                            excludeFromPatchNote));
        } else {
            documentMetadata.changeEmbeddingStatus(EmbeddingStatus.NONE);
            deleteEvent(projectId, documentMetadata.getId());
        }
    }

    public void deleteEvent(UUID projectId, Long documentId) {
        eventPublisher.publishEvent(new DocumentVectorDeleteEvent(projectId, documentId));
    }

    private boolean isEmbeddable(String extension) {
        AllowedFileType fileType = AllowedFileType.fromExtension(extension);
        return fileType != null && fileType.isEmbeddable();
    }
}

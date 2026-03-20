package kr.java.documind.domain.archive.document.event;

import java.time.OffsetDateTime;
import java.util.List;
import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.archive.vector.event.EmbeddingCompletedEvent;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import kr.java.documind.domain.notification.event.DocumentNotificationEvent;
import kr.java.documind.domain.notification.model.enums.NotificationEventType;
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
public class EmbeddingCompletedEventHandler {

    private final DocumentMetadataManager documentMetadataManager;
    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    @Transactional
    public void handle(EmbeddingCompletedEvent event) {
        DocumentMetadata metadata = documentMetadataManager.findById(event.sourceId()).orElse(null);
        if (metadata == null) {
            return;
        }

        String publicId = resolvePublicId(event);
        publishNotification(event, metadata, publicId);

        if (event.status() == EmbeddingStatus.SUCCESS) {
            publishDocumentEmbeddedEvent(metadata, event.excludeFromPatchNote());
        }
    }

    private String resolvePublicId(EmbeddingCompletedEvent event) {
        return projectRepository.findById(event.projectId()).map(Project::getPublicId).orElse(null);
    }

    private void publishNotification(
            EmbeddingCompletedEvent event, DocumentMetadata metadata, String publicId) {
        boolean isSuccess = event.status() == EmbeddingStatus.SUCCESS;
        NotificationEventType eventType =
                isSuccess
                        ? NotificationEventType.EMBEDDING_SUCCESS
                        : NotificationEventType.EMBEDDING_FAILED;

        String title = isSuccess ? "문서 임베딩 완료" : "문서 임베딩 실패";
        String message =
                isSuccess
                        ? String.format("'%s' 문서의 임베딩이 완료되었습니다.", metadata.getDocumentName())
                        : String.format("'%s' 문서의 임베딩이 실패했습니다.", metadata.getDocumentName());

        String relatedUrl = null;
        if (publicId != null) {
            relatedUrl =
                    isSuccess
                            ? String.format("/projects/%s/documents/%d", publicId, metadata.getId())
                            : String.format(
                                    "/projects/%s/documents/%d/retry-embedding",
                                    publicId, metadata.getId());
        }

        eventPublisher.publishEvent(
                new DocumentNotificationEvent(
                        event.projectId(),
                        List.of(event.memberId()),
                        metadata.getId(),
                        eventType,
                        title,
                        message,
                        relatedUrl,
                        true));

        log.debug(
                "[EmbeddingCompleted] 알림 발행 - sourceId: {}, status: {}, memberId: {}",
                event.sourceId(),
                event.status(),
                event.memberId());
    }

    private void publishDocumentEmbeddedEvent(
            DocumentMetadata metadata, boolean excludeFromPatchNote) {
        DocumentGroup group = metadata.getDocumentGroup();
        boolean isNewDocument = metadata.getReuploadedAt() == null;

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
                "[EmbeddingCompleted] DocumentEmbeddedEvent 발행 - sourceId: {}, isNew: {}, exclude: {}",
                metadata.getId(),
                isNewDocument,
                excludeFromPatchNote);
    }
}

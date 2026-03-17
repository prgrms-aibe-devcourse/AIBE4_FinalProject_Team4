package kr.java.documind.domain.archive.document.model.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;

public record DocumentMetadataResponse(
        Long documentId,
        String documentName,
        String extension,
        String version,
        boolean isProcessed,
        EmbeddingStatus embeddingStatus,
        OffsetDateTime uploadedAt,
        OffsetDateTime reuploadedAt) {

    public static DocumentMetadataResponse from(DocumentMetadata metadata) {
        return new DocumentMetadataResponse(
                metadata.getId(),
                metadata.getDocumentName(),
                metadata.getExtension(),
                metadata.getVersionString(),
                metadata.isProcessed(),
                metadata.getEmbeddingStatus(),
                metadata.getUploadedAt(),
                metadata.getReuploadedAt());
    }
}

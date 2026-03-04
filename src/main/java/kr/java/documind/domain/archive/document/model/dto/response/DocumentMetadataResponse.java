package kr.java.documind.domain.archive.document.model.dto.response;

import java.time.LocalDateTime;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;

public record DocumentMetadataResponse(
        Long documentId,
        String documentName,
        String extension,
        String version,
        LocalDateTime uploadedAt,
        LocalDateTime reuploadedAt) {

    public static DocumentMetadataResponse from(DocumentMetadata metadata) {
        return new DocumentMetadataResponse(
                metadata.getId(),
                metadata.getDocumentName(),
                metadata.getExtension(),
                metadata.getVersionString(),
                metadata.getUploadedAt(),
                metadata.getReuploadedAt());
    }
}

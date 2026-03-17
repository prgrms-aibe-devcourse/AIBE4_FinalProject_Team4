package kr.java.documind.domain.archive.document.model.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;

public record DocumentDetailResponse(
        Long documentId,
        String documentName,
        String extension,
        String version,
        String groupName,
        String category,
        boolean isProcessed,
        EmbeddingStatus embeddingStatus,
        OffsetDateTime uploadedAt,
        OffsetDateTime reuploadedAt,
        List<DocumentMetadataResponse> versions) {

    public static DocumentDetailResponse of(
            DocumentMetadata metadata,
            DocumentGroup group,
            List<DocumentMetadataResponse> versions) {
        return new DocumentDetailResponse(
                metadata.getId(),
                metadata.getDocumentName(),
                metadata.getExtension(),
                metadata.getVersionString(),
                group.getGroupName(),
                group.getCategory(),
                metadata.isProcessed(),
                metadata.getEmbeddingStatus(),
                metadata.getUploadedAt(),
                metadata.getReuploadedAt(),
                versions);
    }
}

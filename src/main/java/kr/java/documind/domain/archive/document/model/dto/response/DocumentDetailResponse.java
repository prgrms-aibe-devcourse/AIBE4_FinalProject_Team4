package kr.java.documind.domain.archive.document.model.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;

public record DocumentDetailResponse(
        Long documentId,
        String documentName,
        String extension,
        String version,
        String groupName,
        String category,
        boolean isProcessed,
        LocalDateTime uploadedAt,
        LocalDateTime reuploadedAt,
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
                metadata.getUploadedAt(),
                metadata.getReuploadedAt(),
                versions);
    }
}

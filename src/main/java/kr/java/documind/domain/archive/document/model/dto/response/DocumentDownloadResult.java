package kr.java.documind.domain.archive.document.model.dto.response;

import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import org.springframework.core.io.Resource;

public record DocumentDownloadResult(
        Resource resource, String downloadFilename, String contentType) {

    public static DocumentDownloadResult of(Resource resource, DocumentMetadata metadata) {
        String filename = metadata.getDocumentName() + "." + metadata.getExtension();
        String contentType = resolveContentType(metadata.getExtension());
        return new DocumentDownloadResult(resource, filename, contentType);
    }

    private static String resolveContentType(String extension) {
        return switch (extension.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "application/octet-stream";
        };
    }
}

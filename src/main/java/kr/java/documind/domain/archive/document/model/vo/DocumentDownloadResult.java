package kr.java.documind.domain.archive.document.model.vo;

import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.global.enums.AllowedFileType;
import org.springframework.core.io.Resource;

public record DocumentDownloadResult(
        Resource resource, String downloadFilename, String contentType) {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    public static DocumentDownloadResult of(Resource resource, DocumentMetadata metadata) {
        String filename = metadata.getDocumentName() + "." + metadata.getExtension();
        String contentType = resolveContentType(metadata.getExtension());
        return new DocumentDownloadResult(resource, filename, contentType);
    }

    private static String resolveContentType(String extension) {
        AllowedFileType fileType = AllowedFileType.fromExtension(extension);
        return fileType != null ? fileType.getMimeType() : DEFAULT_CONTENT_TYPE;
    }
}

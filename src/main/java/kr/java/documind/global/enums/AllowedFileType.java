package kr.java.documind.global.enums;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

@Getter
public enum AllowedFileType {
    JPG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    TXT("text/plain", "txt"),
    PDF("application/pdf", "pdf"),
    DOC("application/msword", "doc"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    XLS("application/vnd.ms-excel", "xls"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    PPT("application/vnd.ms-powerpoint", "ppt"),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");

    private static final Map<String, AllowedFileType> BY_MIME_TYPE;
    private static final Map<String, AllowedFileType> BY_EXTENSION;

    static {
        Map<String, AllowedFileType> mimeMap = new HashMap<>();
        Map<String, AllowedFileType> extMap = new HashMap<>();
        for (AllowedFileType type : values()) {
            mimeMap.put(type.mimeType, type);
            extMap.put(type.extension, type);
        }
        BY_MIME_TYPE = Map.copyOf(mimeMap);
        BY_EXTENSION = Map.copyOf(extMap);
    }

    private final String mimeType;
    private final String extension;

    AllowedFileType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public static AllowedFileType fromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return null;
        }
        return BY_MIME_TYPE.get(mimeType.strip().toLowerCase());
    }

    public static AllowedFileType fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }
        return BY_EXTENSION.get(extension.strip().toLowerCase());
    }
}

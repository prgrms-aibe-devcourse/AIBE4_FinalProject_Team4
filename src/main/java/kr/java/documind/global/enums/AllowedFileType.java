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

    static {
        Map<String, AllowedFileType> map = new HashMap<>();
        for (AllowedFileType type : values()) {
            map.put(type.mimeType, type);
        }
        BY_MIME_TYPE = Map.copyOf(map);
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
}

package kr.java.documind.domain.archive.document.model.repository;

public interface DocumentGroupSummary {

    Long getGroupId();

    String getGroupName();

    String getCategory();

    Long getVersionOrdinal();

    Long getDocumentCount();

    default String getLatestVersion() {
        Long ordinal = getVersionOrdinal();
        if (ordinal == null) {
            return "v0.0.0";
        }
        long major = ordinal / 1_000_000;
        long minor = (ordinal % 1_000_000) / 1_000;
        long patch = ordinal % 1_000;
        return "v" + major + "." + minor + "." + patch;
    }
}

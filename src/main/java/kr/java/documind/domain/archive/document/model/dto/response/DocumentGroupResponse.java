package kr.java.documind.domain.archive.document.model.dto.response;

import kr.java.documind.domain.archive.document.model.repository.DocumentGroupSummary;

public record DocumentGroupResponse(
        Long groupId, String groupName, String category, String latestVersion, long documentCount) {

    public static DocumentGroupResponse from(DocumentGroupSummary summary) {
        return new DocumentGroupResponse(
                summary.getGroupId(),
                summary.getGroupName(),
                summary.getCategory(),
                formatVersion(summary.getVersionOrdinal()),
                summary.getDocumentCount());
    }

    private static String formatVersion(Long ordinal) {
        if (ordinal == null) {
            return "v0.0.0";
        }
        long major = ordinal / 1_000_000;
        long minor = (ordinal % 1_000_000) / 1_000;
        long patch = ordinal % 1_000;
        return "v" + major + "." + minor + "." + patch;
    }
}

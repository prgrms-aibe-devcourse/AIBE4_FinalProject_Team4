package kr.java.documind.domain.archive.document.model.dto.response;

import kr.java.documind.domain.archive.document.model.repository.DocumentGroupSummary;

public record DocumentGroupResponse(
        Long groupId, String groupName, String category, String latestVersion, long documentCount) {

    public static DocumentGroupResponse from(DocumentGroupSummary summary) {
        return new DocumentGroupResponse(
                summary.getGroupId(),
                summary.getGroupName(),
                summary.getCategory(),
                summary.getLatestVersion(),
                summary.getDocumentCount());
    }
}

package kr.java.documind.domain.archive.document.model.dto.response;

import kr.java.documind.domain.archive.document.model.vo.DocumentGroupSummaryResult;

public record DocumentGroupResponse(
        Long groupId, String groupName, String category, String latestVersion, long documentCount) {

    public static DocumentGroupResponse from(DocumentGroupSummaryResult summary) {
        return new DocumentGroupResponse(
                summary.groupId(),
                summary.groupName(),
                summary.category(),
                summary.latestVersion(),
                summary.documentCount());
    }
}

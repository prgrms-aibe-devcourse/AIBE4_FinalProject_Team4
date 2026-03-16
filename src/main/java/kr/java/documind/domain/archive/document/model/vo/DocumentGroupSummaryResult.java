package kr.java.documind.domain.archive.document.model.vo;

public record DocumentGroupSummaryResult(
        Long groupId,
        String groupName,
        String category,
        String latestVersion,
        long documentCount) {}

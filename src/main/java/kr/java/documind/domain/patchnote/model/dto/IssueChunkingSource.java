package kr.java.documind.domain.patchnote.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IssueChunkingSource(
        Long issueId,
        UUID projectId,
        String title,
        String description,
        String resolutionNote,
        String severity,
        String issueType,
        Instant resolvedAt,
        List<IssueCommentChunkSource> comments) {

    public IssueChunkingSource {
        comments = comments == null ? List.of() : List.copyOf(comments);
    }
}

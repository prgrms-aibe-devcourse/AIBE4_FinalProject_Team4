package kr.java.documind.domain.patchnote.model.dto;

import java.time.Instant;

public record IssueCommentChunkSource(
    Long commentId,
    String content,
    Instant createdAt) {}

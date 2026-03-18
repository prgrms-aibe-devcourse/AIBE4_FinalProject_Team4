package kr.java.documind.domain.patchnote.service;

import java.util.HashMap;
import java.util.Map;
import kr.java.documind.domain.patchnote.model.dto.IssueChunkAnalysis;
import kr.java.documind.domain.patchnote.model.dto.IssueChunkDraft;
import kr.java.documind.domain.patchnote.model.dto.IssueChunkingSource;
import kr.java.documind.domain.patchnote.model.dto.IssueCommentChunkSource;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Component
public class IssueChunkDocumentBuilder {

    public Document build(
            IssueChunkingSource source,
            IssueChunkDraft draft,
            int chunkIndex,
            int totalChunks,
            IssueChunkAnalysis analysis) {

        String text = buildText(source, draft);
        Map<String, Object> metadata =
                buildMetadata(source, draft, chunkIndex, totalChunks, analysis);

        return new Document(text, metadata);
    }

    private String buildText(IssueChunkingSource source, IssueChunkDraft draft) {

        return switch (draft.chunkRole()) {
            case "background" -> buildBackgroundText(source);
            case "resolution" -> buildResolutionText(source);
            case "background_resolution" -> buildMergedText(source);
            case "comment" -> buildCommentText(source, draft);
            default -> throw new IllegalArgumentException(
                    "Unknown chunkRole: " + draft.chunkRole());
        };
    }

    private String buildBackgroundText(IssueChunkingSource source) {
        StringBuilder sb = new StringBuilder();
        sb.append("이 문서는 게임 이슈의 배경 정보입니다.");
        sb.append("\n제목: ").append(defaultString(source.title(), "제목 없음"));

        if (hasText(source.description())) {
            sb.append("\n설명: ").append(source.description().trim());
        }

        sb.append("\n심각도: ").append(defaultString(source.severity(), "UNKNOWN"));
        sb.append("\n이슈 유형: ").append(defaultString(source.issueType(), "UNKNOWN"));

        return sb.toString();
    }

    private String buildResolutionText(IssueChunkingSource source) {
        return "이 문서는 게임 이슈의 해결 정보입니다.\n제목: "
                + defaultString(source.title(), "제목 없음")
                + "\n해결 내용: "
                + defaultString(source.resolutionNote(), "");
    }

    private String buildMergedText(IssueChunkingSource source) {
        StringBuilder sb = new StringBuilder();
        sb.append("이 문서는 게임 이슈의 배경과 해결 정보를 함께 담고 있습니다.");
        sb.append("\n제목: ").append(defaultString(source.title(), "제목 없음"));

        if (hasText(source.description())) {
            sb.append("\n설명: ").append(source.description().trim());
        }

        sb.append("\n심각도: ").append(defaultString(source.severity(), "UNKNOWN"));
        sb.append("\n이슈 유형: ").append(defaultString(source.issueType(), "UNKNOWN"));
        sb.append("\n해결 내용: ").append(defaultString(source.resolutionNote(), ""));

        return sb.toString();
    }

    private String buildCommentText(IssueChunkingSource source, IssueChunkDraft draft) {

        StringBuilder sb = new StringBuilder();
        sb.append("이 문서는 게임 이슈 관련 댓글입니다.");
        sb.append("\n이슈 제목: ").append(defaultString(source.title(), "제목 없음"));

        IssueCommentChunkSource comment = source.comments().get(draft.sourceIndex());

        sb.append("\n댓글 내용: ").append(defaultString(comment.content(), ""));

        if (comment.createdAt() != null) {
            sb.append("\n댓글 작성 시각: ").append(comment.createdAt());
        }

        return sb.toString();
    }

    private Map<String, Object> buildMetadata(
            IssueChunkingSource source,
            IssueChunkDraft draft,
            int chunkIndex,
            int totalChunks,
            IssueChunkAnalysis analysis) {

        Map<String, Object> metadata = new HashMap<>();

        metadata.put("document_id", buildDocumentId(source.issueId(), draft));
        metadata.put("project_id", source.projectId().toString());
        metadata.put("source_type", "ISSUE");
        metadata.put("source_id", source.issueId());

        metadata.put("chunk_role", draft.chunkRole());
        metadata.put("chunk_index", chunkIndex);
        metadata.put("total_chunks", totalChunks);

        metadata.put("issue_title", defaultString(source.title(), "UNTITLED"));
        metadata.put("severity", defaultString(source.severity(), "UNKNOWN"));
        metadata.put("error_type", defaultString(source.issueType(), "UNKNOWN"));

        metadata.put("has_resolution", analysis.hasResolution());
        metadata.put("chunk_contains_resolution", draft.isResolutionLike());
        metadata.put("has_numeric_change", analysis.hasNumericChange());
        metadata.put("affects_player", analysis.affectsPlayer());

        if (source.resolvedAt() != null) {
            metadata.put("resolved_at", source.resolvedAt().toString());
        }

        if (draft.commentId() != null) {
            metadata.put("comment_id", draft.commentId());
        }

        if (draft.commentIndex() != null) {
            metadata.put("comment_index", draft.commentIndex());
        }

        return metadata;
    }

    private String buildDocumentId(Long issueId, IssueChunkDraft draft) {
        if (draft.commentIndex() != null) {
            return "ISSUE:%d:%s:%d".formatted(issueId, draft.chunkRole(), draft.commentIndex());
        }
        return "ISSUE:%d:%s".formatted(issueId, draft.chunkRole());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String defaultString(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }
}

package kr.java.documind.domain.patchnote.model.dto;

public record IssueChunkDraft(String chunkRole, Long commentId, Integer sourceIndex, Integer commentIndex) {

    public static IssueChunkDraft background() {
        return new IssueChunkDraft("background", null, null, null);
    }

    public static IssueChunkDraft resolution() {
        return new IssueChunkDraft("resolution", null, null, null);
    }

    public static IssueChunkDraft merged() {
        return new IssueChunkDraft("background_resolution", null, null, null);
    }

    public static IssueChunkDraft comment(Long commentId, int sourceIndex, int commentIndex) {
        return new IssueChunkDraft("comment", commentId, sourceIndex, commentIndex);
    }

    public boolean isResolutionLike() {
        return "resolution".equals(chunkRole) || "background_resolution".equals(chunkRole);
    }

    public boolean isComment() {
        return "comment".equals(chunkRole);
    }
}

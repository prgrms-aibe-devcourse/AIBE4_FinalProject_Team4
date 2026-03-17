package kr.java.documind.domain.patchnote.model.dto;

public record IssueChunkAnalysis(
    boolean hasResolution,
    boolean hasNumericChange,
    boolean affectsPlayer) {}

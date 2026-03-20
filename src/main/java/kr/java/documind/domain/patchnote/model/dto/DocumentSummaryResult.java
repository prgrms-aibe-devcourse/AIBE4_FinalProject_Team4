package kr.java.documind.domain.patchnote.model.dto;

public record DocumentSummaryResult(
        String title, String summary, String categoryFromLlm, boolean affectsPlayer) {}

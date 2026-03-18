package kr.java.documind.domain.patchnote.model.dto;

public record TokenEstimation(
        int estimatedTokens, int tokenLimit, boolean exceeded, int itemCount) {
    public double usageRatio() {
        return (double) estimatedTokens / tokenLimit;
    }
}

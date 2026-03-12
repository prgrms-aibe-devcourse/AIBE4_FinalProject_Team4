package kr.java.documind.domain.issue.model.dto.response;

import lombok.Builder;

/**
 * 이슈 유사도 분석 결과
 *
 * @param matchType 매칭 타입 (EXACT_MATCH, HIGHLY_SIMILAR, SIMILAR, NO_MATCH)
 * @param similarity 유사도 점수 (0.0 ~ 100.0)
 * @param matchedIssueId 유사한 이슈 ID (없으면 null)
 * @param matchedIssueTitle 유사한 이슈 제목 (없으면 null)
 * @param reason 판단 근거 설명
 * @param details 상세 점수 (fingerprint, errorType, stack, message)
 */
@Builder
public record SimilarityResult(
        String matchType,
        Double similarity,
        Long matchedIssueId,
        String matchedIssueTitle,
        String reason,
        SimilarityDetails details) {

    public static SimilarityResult exactMatch(Long issueId, String issueTitle) {
        return SimilarityResult.builder()
                .matchType("EXACT_MATCH")
                .similarity(100.0)
                .matchedIssueId(issueId)
                .matchedIssueTitle(issueTitle)
                .reason("동일한 Fingerprint를 가진 기존 이슈가 존재합니다.")
                .details(
                        SimilarityDetails.builder()
                                .fingerprintScore(100.0)
                                .errorTypeScore(100.0)
                                .stackScore(100.0)
                                .messageScore(100.0)
                                .build())
                .build();
    }

    public static SimilarityResult highlySimilar(
            Long issueId, String issueTitle, Double similarity, SimilarityDetails details) {
        return SimilarityResult.builder()
                .matchType("HIGHLY_SIMILAR")
                .similarity(similarity)
                .matchedIssueId(issueId)
                .matchedIssueTitle(issueTitle)
                .reason(buildSimilarReason(similarity, details))
                .details(details)
                .build();
    }

    public static SimilarityResult similar(
            Long issueId, String issueTitle, Double similarity, SimilarityDetails details) {
        return SimilarityResult.builder()
                .matchType("SIMILAR")
                .similarity(similarity)
                .matchedIssueId(issueId)
                .matchedIssueTitle(issueTitle)
                .reason(buildSimilarReason(similarity, details))
                .details(details)
                .build();
    }

    public static SimilarityResult noMatch() {
        return SimilarityResult.builder()
                .matchType("NO_MATCH")
                .similarity(0.0)
                .matchedIssueId(null)
                .matchedIssueTitle(null)
                .reason("유사한 기존 이슈가 발견되지 않았습니다. 신규 이슈로 분류됩니다.")
                .details(null)
                .build();
    }

    private static String buildSimilarReason(Double similarity, SimilarityDetails details) {
        StringBuilder reason = new StringBuilder();
        reason.append(
                String.format("기존 이슈와 %.1f%% 유사합니다. ", similarity));

        if (details.errorTypeScore() == 100.0) {
            reason.append("동일한 에러 타입이며, ");
        }

        if (details.stackScore() >= 70.0) {
            reason.append(String.format("스택 트레이스가 %.0f%% 일치합니다.", details.stackScore()));
        } else if (details.stackScore() >= 50.0) {
            reason.append("스택 트레이스가 부분적으로 일치합니다.");
        }

        return reason.toString();
    }

    @Builder
    public record SimilarityDetails(
            Double fingerprintScore,
            Double errorTypeScore,
            Double stackScore,
            Double messageScore) {}
}

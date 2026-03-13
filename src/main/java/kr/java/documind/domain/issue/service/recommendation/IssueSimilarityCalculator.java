package kr.java.documind.domain.issue.service.recommendation;

import java.util.HashSet;
import java.util.Set;
import kr.java.documind.domain.issue.model.dto.response.SimilarityResult;
import kr.java.documind.domain.issue.model.entity.Issue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 이슈 유사도 계산기 (복합 접근 방식)
 *
 * <p>가중치: - Stack Trace: 60% (Jaccard Similarity) - Error Type: 20% (동일 여부) - Message: 20%
 * (Levenshtein Similarity)
 *
 * <p>참고: Fingerprint 비교는 IssueRecommendationService에서 사전 필터링되므로 이 계산기에 들어오는 이슈들은 모두
 * Fingerprint가 다름이 보장됨
 */
@Component
@Slf4j
public class IssueSimilarityCalculator {

    private static final double ERROR_TYPE_WEIGHT = 0.2;
    private static final double STACK_WEIGHT = 0.6;
    private static final double MESSAGE_WEIGHT = 0.2;

    private static final double HIGHLY_SIMILAR_THRESHOLD = 70.0;
    private static final double SIMILAR_THRESHOLD = 40.0;

    /**
     * 두 이슈 간의 종합 유사도를 계산합니다.
     *
     * @param issue1 비교 대상 이슈 1
     * @param issue2 비교 대상 이슈 2
     * @return 유사도 결과 (0.0 ~ 100.0)
     */
    public SimilarityResult calculate(Issue issue1, Issue issue2) {
        log.debug(
                "Calculating similarity between issue {} and issue {}",
                issue1.getId(),
                issue2.getId());
        log.debug("Issue1 stackKey: {}", issue1.getStackKey());
        log.debug("Issue2 stackKey: {}", issue2.getStackKey());

        // 1. Error Type 비교 (20%)
        double errorTypeScore = calculateErrorTypeScore(issue1, issue2);

        // 2. Stack Trace Jaccard Similarity (60%)
        double stackScore = calculateStackSimilarity(issue1.getStackKey(), issue2.getStackKey());

        // 3. Message Levenshtein Similarity (20%)
        double messageScore = calculateLevenshteinSimilarity(issue1.getTitle(), issue2.getTitle());

        // 종합 점수 계산
        double totalSimilarity =
                errorTypeScore * ERROR_TYPE_WEIGHT
                        + stackScore * STACK_WEIGHT
                        + messageScore * MESSAGE_WEIGHT;

        log.debug(
                "Similarity scores - errorType: {}, stack: {}, message: {}, total: {}",
                errorTypeScore,
                stackScore,
                messageScore,
                totalSimilarity);

        SimilarityResult.SimilarityDetails details =
                SimilarityResult.SimilarityDetails.builder()
                        .fingerprintScore(null)
                        .errorTypeScore(errorTypeScore)
                        .stackScore(stackScore)
                        .messageScore(messageScore)
                        .build();

        // 다단계 임계값 적용
        if (totalSimilarity >= HIGHLY_SIMILAR_THRESHOLD) {
            return SimilarityResult.highlySimilar(
                    issue2.getId(), issue2.getTitle(), totalSimilarity, details);
        } else if (totalSimilarity >= SIMILAR_THRESHOLD) {
            return SimilarityResult.similar(
                    issue2.getId(), issue2.getTitle(), totalSimilarity, details);
        }

        return SimilarityResult.noMatch();
    }

    /** Error Type 일치 여부 (100 or 0) */
    private double calculateErrorTypeScore(Issue issue1, Issue issue2) {
        if (issue1.getErrorType() == null || issue2.getErrorType() == null) {
            return 0.0;
        }
        return issue1.getErrorType().equals(issue2.getErrorType()) ? 100.0 : 0.0;
    }

    /**
     * Stack Trace Jaccard Similarity
     *
     * <p>스택 트레이스를 토큰으로 분해하여 유사도 계산
     *
     * <p>예: "at UserService.getProfile:42" → ["at", "UserService", "getProfile", "42"]
     *
     * <p>교집합 크기 / 합집합 크기
     */
    private double calculateStackSimilarity(String stack1, String stack2) {
        if (stack1 == null || stack2 == null || stack1.isBlank() || stack2.isBlank()) {
            return 0.0;
        }

        // 각 스택을 토큰 집합으로 변환
        Set<String> tokens1 = tokenizeStack(stack1);
        Set<String> tokens2 = tokenizeStack(stack2);

        // 교집합
        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        // 합집합
        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size() * 100.0;
    }

    /**
     * 스택 트레이스를 토큰으로 분해
     *
     * <p>여러 줄 스택트레이스와 한 줄 스택 키 모두 지원
     *
     * <p>구분자: 공백, 점(.), 콜론(:), 괄호, 슬래시
     *
     * @param stack 스택 트레이스 문자열
     * @return 토큰 집합
     */
    private Set<String> tokenizeStack(String stack) {
        Set<String> tokens = new HashSet<>();

        // 여러 줄로 나누기 (줄바꿈이 있으면)
        String[] lines = stack.split("\\r?\\n");

        for (String line : lines) {
            // 각 줄을 토큰으로 분리
            // 구분자: 공백, ., :, (, ), /, \
            String[] lineTokens = line.split("[\\s.:()//\\\\]+");

            for (String token : lineTokens) {
                // 빈 문자열이 아니고, 의미 있는 토큰만 추가
                if (!token.isBlank() && token.length() > 1) {
                    tokens.add(token.toLowerCase()); // 대소문자 무시
                }
            }
        }

        log.debug("Tokenized stack '{}' into {} tokens: {}", stack, tokens.size(), tokens);
        return tokens;
    }

    /**
     * Levenshtein Distance 기반 문자열 유사도
     *
     * <p>편집 거리가 짧을수록 유사도가 높음
     */
    private double calculateLevenshteinSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return 0.0;
        }

        if (str1.equals(str2)) {
            return 100.0;
        }

        int distance = levenshteinDistance(str1, str2);
        int maxLength = Math.max(str1.length(), str2.length());

        if (maxLength == 0) {
            return 100.0;
        }

        return (1.0 - (double) distance / maxLength) * 100.0;
    }

    /** Levenshtein Distance 계산 (동적 프로그래밍) */
    private int levenshteinDistance(String str1, String str2) {
        int[][] dp = new int[str1.length() + 1][str2.length() + 1];

        for (int i = 0; i <= str1.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= str2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                int cost = str1.charAt(i - 1) == str2.charAt(j - 1) ? 0 : 1;
                dp[i][j] =
                        Math.min(
                                Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                                dp[i - 1][j - 1] + cost);
            }
        }

        return dp[str1.length()][str2.length()];
    }
}

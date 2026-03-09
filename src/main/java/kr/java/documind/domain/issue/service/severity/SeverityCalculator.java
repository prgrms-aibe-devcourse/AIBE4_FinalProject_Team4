package kr.java.documind.domain.issue.service.severity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.issue.model.vo.SeverityScore;
import kr.java.documind.domain.issue.service.severity.strategy.SeverityStrategy;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 심각도 점수 계산기
 *
 * <p>5가지 Strategy를 사용하여 이슈의 심각도를 자동 판별
 *
 * <p>점수 산정 방식:
 *
 * <ul>
 *   <li>각 Strategy가 독립적으로 점수 계산 (CRASH 50점, PLAYER_COUNT 20점, BUSINESS_IMPACT 30점, BLOCKING 20점,
 *       FREQUENCY 20점)
 *   <li>합산 점수 = 140점 만점 (원점수, rawScore)
 *   <li>최종 점수 = Math.min(원점수, 100) → 100점 만점으로 캡핑
 *   <li>최종 점수에 따라 IssueSeverity 자동 매핑 (CRITICAL: 90-100, HIGH: 60-89, MEDIUM: 30-59, LOW: 0-29)
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeverityCalculator {

    private final List<SeverityStrategy> strategies;

    /**
     * 이슈의 심각도 점수 계산
     *
     * @param issue 이슈
     * @param gameLog 로그
     * @return 심각도 점수 (SeverityScore VO)
     */
    public SeverityScore calculate(Issue issue, GameLog gameLog) {
        Map<SeverityFactor, Integer> scoreBreakdown = new HashMap<>();
        int rawScore = 0;

        // 각 Strategy 실행 및 점수 수집
        for (SeverityStrategy strategy : strategies) {
            int score = strategy.calculate(issue, gameLog);
            SeverityFactor factor = strategy.getFactor();

            scoreBreakdown.put(factor, score);
            rawScore += score;

            log.debug(
                    "Strategy 실행: {} → {}점 (이슈 ID: {}, Fingerprint: {})",
                    factor.name(),
                    score,
                    issue.getId(),
                    issue.getFingerprint());
        }

        // 최종 점수 계산 (100점 만점 캡핑)
        int totalScore = Math.min(rawScore, 100);

        // 심각도 등급 자동 판별
        IssueSeverity severity = IssueSeverity.fromScore(totalScore);

        // 사유 텍스트 생성
        String reason = generateReasonText(scoreBreakdown, issue, gameLog);

        log.debug(
                "심각도 계산 완료: {} ({}점/{}) - 이슈 ID: {}, Fingerprint: {}",
                severity.getValue(),
                totalScore,
                rawScore,
                issue.getId(),
                issue.getFingerprint());

        return SeverityScore.builder()
                .severity(severity)
                .totalScore(totalScore)
                .rawScore(rawScore)
                .scoreBreakdown(scoreBreakdown)
                .reason(reason)
                .build();
    }

    /**
     * 심각도 사유 텍스트 생성
     *
     * <p>예시: "CRITICAL: 치명적 크래시 (OUT_OF_MEMORY, 50점), 플레이어 1,234명 영향 (20점)"
     *
     * @param scoreBreakdown 요소별 점수
     * @param issue 이슈
     * @param log 로그
     * @return 사유 텍스트
     */
    private String generateReasonText(
            Map<SeverityFactor, Integer> scoreBreakdown, Issue issue, GameLog log) {
        List<String> reasons =
                strategies.stream()
                        .filter(strategy -> scoreBreakdown.get(strategy.getFactor()) > 0)
                        .map(
                                strategy ->
                                        strategy.generateReason(
                                                scoreBreakdown.get(strategy.getFactor()),
                                                issue,
                                                log))
                        .filter(reason -> reason != null && !reason.isBlank())
                        .collect(Collectors.toList());

        if (reasons.isEmpty()) {
            return "점수 없음";
        }

        return String.join(", ", reasons);
    }
}

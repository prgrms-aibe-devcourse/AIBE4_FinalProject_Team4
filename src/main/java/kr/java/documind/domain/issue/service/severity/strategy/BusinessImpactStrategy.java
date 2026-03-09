package kr.java.documind.domain.issue.service.severity.strategy;

import java.util.Map;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.global.config.SeverityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 비즈니스 임팩트 기반 심각도 점수 계산 (0-30점)
 *
 * <p>매출 관련 키워드 감지 시 점수 자동 가산
 *
 * <p>점수 기준: application.yml의 issue.severity.business-impact.keywords 설정 참조
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessImpactStrategy implements SeverityStrategy {

    private final SeverityProperties severityProperties;

    @Override
    public int calculate(Issue issue, GameLog log) {
        int maxScore = 0;

        // 1. 이슈 제목에서 키워드 검색
        maxScore = Math.max(maxScore, detectKeywords(issue.getTitle()));

        // 2. 이슈 설명에서 키워드 검색
        if (issue.getDescription() != null) {
            maxScore = Math.max(maxScore, detectKeywords(issue.getDescription()));
        }

        // 3. 로그 본문(archive)에서 키워드 검색
        maxScore = Math.max(maxScore, detectKeywords(log.getArchive()));

        // 4. 로그 attributes에서 키워드 검색 (옵션)
        if (log.getAttributes() != null) {
            String attributesStr = log.getAttributes().toString().toLowerCase();
            maxScore = Math.max(maxScore, detectKeywords(attributesStr));
        }

        // 최대 30점 제한
        return Math.min(maxScore, 30);
    }

    /**
     * 텍스트에서 매출 키워드 감지 (설정값 기반)
     *
     * @param text 검색 대상 텍스트
     * @return 감지된 키워드 중 최대 점수
     */
    private int detectKeywords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        String lowerText = text.toLowerCase();
        Map<String, Integer> keywords = severityProperties.getBusinessImpact().getKeywords();

        return keywords.entrySet().stream()
                .filter(entry -> lowerText.contains(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .max()
                .orElse(0);
    }

    @Override
    public SeverityFactor getFactor() {
        return SeverityFactor.BUSINESS_IMPACT;
    }

    @Override
    public String generateReason(int score, Issue issue, GameLog log) {
        if (score == 0) {
            return null;
        }

        String category =
                switch (score) {
                    case 30 -> "결제 시스템 영향";
                    case 25 -> "가챠/상점 영향";
                    case 20 -> "게임 밸런스 영향";
                    case 15 -> "게임 진행 차단";
                    case 10 -> "소셜 기능 영향";
                    case 5 -> "일반 기능 영향";
                    default -> "비즈니스 영향";
                };

        return String.format("%s (%d점)", category, score);
    }
}

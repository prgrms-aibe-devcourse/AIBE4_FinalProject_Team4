package kr.java.documind.domain.issue.service.severity.strategy;

import java.util.Map;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.global.config.SeverityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 게임 진행 차단 여부 기반 심각도 점수 계산 (0-20점)
 *
 * <p>점수 기준: application.yml의 issue.severity.blocking 설정 참조
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockingStrategy implements SeverityStrategy {

    private final SeverityProperties severityProperties;

    @Override
    public int calculate(Issue issue, GameLog log) {
        int score = 0;

        // 1. ErrorType 기반 점수
        score = Math.max(score, getScoreByErrorType(issue.getErrorType()));

        // 2. 키워드 기반 점수
        score = Math.max(score, detectBlockingKeywords(issue.getTitle()));
        if (issue.getDescription() != null) {
            score = Math.max(score, detectBlockingKeywords(issue.getDescription()));
        }
        score = Math.max(score, detectBlockingKeywords(log.getArchive()));

        // 최대 20점 제한
        return Math.min(score, 20);
    }

    /**
     * ErrorType에 따른 차단 점수 (설정값 기반)
     *
     * @param errorType 에러 타입
     * @return 점수
     */
    private int getScoreByErrorType(ErrorType errorType) {
        Map<String, Integer> errorTypes = severityProperties.getBlocking().getErrorTypes();
        return errorTypes.getOrDefault(errorType.name(), 0);
    }

    /**
     * 텍스트에서 차단 키워드 감지 (설정값 기반)
     *
     * @param text 검색 대상 텍스트
     * @return 감지된 키워드 중 최대 점수
     */
    private int detectBlockingKeywords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        String lowerText = text.toLowerCase();
        Map<String, Integer> keywords = severityProperties.getBlocking().getKeywords();

        return keywords.entrySet().stream()
                .filter(entry -> lowerText.contains(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .max()
                .orElse(0);
    }

    @Override
    public SeverityFactor getFactor() {
        return SeverityFactor.BLOCKING;
    }

    @Override
    public String generateReason(int score, Issue issue, GameLog gameLog) {
        if (score == 0) {
            return null;
        }

        String level =
                switch (score) {
                    case 20 -> "게임 완전 차단";
                    case 15 -> "메인 진행 차단";
                    case 10 -> "일부 기능 차단";
                    case 5 -> "불편 발생";
                    default -> "진행 차단";
                };

        return String.format("%s (%d점)", level, score);
    }
}

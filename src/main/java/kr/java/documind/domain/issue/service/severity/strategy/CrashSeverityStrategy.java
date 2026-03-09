package kr.java.documind.domain.issue.service.severity.strategy;

import java.util.Map;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.global.config.SeverityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 게임 크래시 여부 기반 심각도 점수 계산 (0-50점)
 *
 * <p>점수 기준: application.yml의 issue.severity.crash.error-types 설정 참조
 */
@Component
@RequiredArgsConstructor
public class CrashSeverityStrategy implements SeverityStrategy {

    private final SeverityProperties severityProperties;

    @Override
    public int calculate(Issue issue, GameLog log) {
        ErrorType errorType = issue.getErrorType();
        Map<String, Integer> errorTypes = severityProperties.getCrash().getErrorTypes();

        return errorTypes.getOrDefault(errorType.name(), 0);
    }

    @Override
    public SeverityFactor getFactor() {
        return SeverityFactor.CRASH;
    }

    @Override
    public String generateReason(int score, Issue issue, GameLog log) {
        if (score == 0) {
            return null;
        }

        ErrorType errorType = issue.getErrorType();
        String severity =
                switch (score) {
                    case 50 -> "치명적 크래시";
                    case 40 -> "서버 크래시 위험";
                    case 30 -> "연결 끊김";
                    case 15 -> "기능 오류";
                    case 10 -> "입력 오류";
                    default -> "에러";
                };

        return String.format("%s (%s, %d점)", severity, errorType.getDescription(), score);
    }
}

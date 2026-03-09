package kr.java.documind.domain.issue.service.severity.strategy;

import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import org.springframework.stereotype.Component;

/**
 * 게임 크래시 여부 기반 심각도 점수 계산 (0-50점)
 *
 * <p>ErrorType에 따라 점수 부여:
 *
 * <ul>
 *   <li>50점: OUT_OF_MEMORY, STACK_OVERFLOW (치명적 크래시)
 *   <li>40점: DATABASE, DEADLOCK (서버 크래시 가능)
 *   <li>30점: NETWORK, TIMEOUT (연결 끊김)
 *   <li>15점: NULL_POINTER, INDEX_OUT_OF_BOUNDS (일부 기능 오류)
 *   <li>10점: ILLEGAL_ARGUMENT, ILLEGAL_STATE (입력 오류)
 *   <li>0점: 기타
 * </ul>
 */
@Component
public class CrashSeverityStrategy implements SeverityStrategy {

    @Override
    public int calculate(Issue issue, GameLog log) {
        ErrorType errorType = issue.getErrorType();

        return switch (errorType) {
            case OUT_OF_MEMORY, STACK_OVERFLOW -> 50; // 치명적
            case DATABASE, DEADLOCK -> 40; // 매우 심각
            case NETWORK, TIMEOUT, IO -> 30; // 심각
            case NULL_POINTER, INDEX_OUT_OF_BOUNDS -> 15; // 보통
            case ILLEGAL_ARGUMENT, ILLEGAL_STATE, UNSUPPORTED_OPERATION -> 10; // 경미
            default -> 0; // 기타
        };
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

package kr.java.documind.domain.issue.service.severity.strategy;

import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;

/**
 * 심각도 점수 계산 전략 인터페이스
 *
 * <p>Strategy Pattern을 사용하여 각 평가 기준별로 독립적인 점수 계산
 *
 * <p>5가지 구현체:
 *
 * <ul>
 *   <li>CrashSeverityStrategy - 게임 크래시 여부 (0-50점)
 *   <li>PlayerCountStrategy - 영향받은 플레이어 수 (0-20점)
 *   <li>BusinessImpactStrategy - 비즈니스 임팩트 (0-30점)
 *   <li>BlockingStrategy - 게임 진행 차단 여부 (0-20점)
 *   <li>FrequencyStrategy - 발생 빈도 (0-20점)
 * </ul>
 */
public interface SeverityStrategy {

    /**
     * 심각도 점수 계산
     *
     * @param issue 이슈 엔티티
     * @param log 게임 로그 (최신 로그 또는 대표 로그)
     * @return 계산된 점수 (0 ~ 해당 요소의 최대 점수)
     */
    int calculate(Issue issue, GameLog log);

    /**
     * 이 전략이 계산하는 요소
     *
     * @return 심각도 계산 요소
     */
    SeverityFactor getFactor();

    /**
     * 점수 계산 근거 텍스트 생성
     *
     * @param score 계산된 점수
     * @param issue 이슈
     * @param log 로그
     * @return 근거 텍스트 (예: "크래시 발생 (50점)")
     */
    default String generateReason(int score, Issue issue, GameLog log) {
        if (score == 0) {
            return null; // 점수가 0이면 근거 텍스트 생략
        }
        return String.format("%s (%d점)", getFactor().getDescription(), score);
    }
}

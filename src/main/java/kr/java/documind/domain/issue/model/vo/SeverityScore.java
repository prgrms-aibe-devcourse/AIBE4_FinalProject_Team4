package kr.java.documind.domain.issue.model.vo;

import java.util.Collections;
import java.util.Map;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import lombok.Builder;
import lombok.Getter;

/**
 * 이슈 심각도 점수 계산 결과를 담는 Value Object
 *
 * <p>불변 객체로 설계되어 있으며, 계산된 점수와 근거를 포함
 */
@Getter
@Builder
public class SeverityScore {

    /** 자동 판별된 심각도 등급 (CRITICAL, HIGH, MEDIUM, LOW) */
    private final IssueSeverity severity;

    /** 총점 (0-100, 캡핑 적용됨) */
    private final int totalScore;

    /** 캡핑 전 원본 점수 (디버깅/분석용) */
    private final int rawScore;

    /** 각 요소별 점수 분해 (CRASH: 50, PLAYER_COUNT: 20, ...) */
    private final Map<SeverityFactor, Integer> scoreBreakdown;

    /** 심각도 판별 이유 텍스트 (예: "CRITICAL: VIP 12명이 결제 실패") */
    private final String reason;

    /**
     * 점수 분해 정보 반환 (불변)
     *
     * @return 각 요소별 점수 Map (수정 불가)
     */
    public Map<SeverityFactor, Integer> getScoreBreakdown() {
        return Collections.unmodifiableMap(scoreBreakdown);
    }

    /**
     * 특정 요소의 점수 조회
     *
     * @param factor 점수 계산 요소
     * @return 해당 요소의 점수 (없으면 0)
     */
    public int getScoreByFactor(SeverityFactor factor) {
        return scoreBreakdown.getOrDefault(factor, 0);
    }

    /**
     * 점수가 100점으로 캡핑되었는지 확인
     *
     * @return 캡핑 여부
     */
    public boolean isCapped() {
        return rawScore > 100;
    }

    /**
     * 디버깅용 문자열 표현
     *
     * @return 점수 정보 요약
     */
    @Override
    public String toString() {
        return String.format(
                "SeverityScore{severity=%s, totalScore=%d, rawScore=%d, reason='%s'}",
                severity, totalScore, rawScore, reason);
    }
}

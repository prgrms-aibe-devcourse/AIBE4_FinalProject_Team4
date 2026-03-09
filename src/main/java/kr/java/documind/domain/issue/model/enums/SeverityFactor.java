package kr.java.documind.domain.issue.model.enums;

/**
 * 이슈 심각도 점수 계산 요소
 *
 * <p>가중치 기반 알고리즘의 5가지 평가 기준
 *
 * <ul>
 *   <li>CRASH: 게임 크래시 여부 (최대 50점)
 *   <li>PLAYER_COUNT: 영향받은 플레이어 수 (최대 20점)
 *   <li>BUSINESS_IMPACT: 비즈니스 임팩트 (최대 30점)
 *   <li>BLOCKING: 게임 진행 차단 여부 (최대 20점)
 *   <li>FREQUENCY: 발생 빈도 (최대 20점)
 * </ul>
 */
public enum SeverityFactor {
    CRASH("게임 크래시 여부", 50),
    PLAYER_COUNT("영향받은 플레이어 수", 20),
    BUSINESS_IMPACT("비즈니스 임팩트", 30),
    BLOCKING("게임 진행 차단 여부", 20),
    FREQUENCY("발생 빈도", 20);

    private final String description;
    private final int maxScore;

    SeverityFactor(String description, int maxScore) {
        this.description = description;
        this.maxScore = maxScore;
    }

    /**
     * 요소 설명 반환
     *
     * @return 요소 설명 (한글)
     */
    public String getDescription() {
        return description;
    }

    /**
     * 최대 점수 반환
     *
     * @return 최대 점수
     */
    public int getMaxScore() {
        return maxScore;
    }

    /**
     * 전체 요소의 최대 점수 합계
     *
     * <p>주의: 140점이지만 실제로는 100점으로 캡핑됨
     *
     * @return 140
     */
    public static int getTotalMaxScore() {
        int total = 0;
        for (SeverityFactor factor : values()) {
            total += factor.maxScore;
        }
        return total;
    }
}

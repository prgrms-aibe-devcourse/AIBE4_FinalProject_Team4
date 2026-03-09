package kr.java.documind.domain.issue.service.severity.strategy;

import java.util.Map;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 비즈니스 임팩트 기반 심각도 점수 계산 (0-30점)
 *
 * <p>매출 관련 키워드 감지 시 점수 자동 가산
 *
 * <p>점수 기준 (Developer Guide 기준):
 *
 * <ul>
 *   <li>결제 시스템 (30점): 인앱 결제, 결제 완료 후 아이템 미지급
 *   <li>가챠/상점 (25점): 가챠 뽑기, 상점 접속 불가
 *   <li>게임 밸런스 (20점): 보스 승률 이상, 아이템 중복 지급
 *   <li>게임 진행 차단 (15점): 메인 퀘스트 진행 불가
 *   <li>소셜 기능 (10점): 친구 초대 실패, 길드 오류
 *   <li>일반 기능 (5점): 튜토리얼, 설정 오류
 * </ul>
 */
@Slf4j
@Component
public class BusinessImpactStrategy implements SeverityStrategy {

    /** 매출 관련 키워드 → 점수 매핑 (최대값 적용) */
    private static final Map<String, Integer> PAYMENT_KEYWORDS =
            Map.ofEntries(
                    // 결제 시스템 (30점)
                    Map.entry("결제", 30),
                    Map.entry("payment", 30),
                    Map.entry("purchase", 30),
                    Map.entry("인앱", 30),
                    Map.entry("iap", 30),
                    Map.entry("billing", 30),
                    // 가챠/상점 (25점)
                    Map.entry("가챠", 25),
                    Map.entry("gacha", 25),
                    Map.entry("상점", 25),
                    Map.entry("shop", 25),
                    Map.entry("store", 25),
                    // 게임 밸런스 (20점)
                    Map.entry("보스", 20),
                    Map.entry("boss", 20),
                    Map.entry("레이드", 20),
                    Map.entry("raid", 20),
                    Map.entry("아이템", 15),
                    Map.entry("item", 15),
                    // 게임 진행 (15점)
                    Map.entry("퀘스트", 15),
                    Map.entry("quest", 15),
                    Map.entry("메인", 15),
                    Map.entry("main", 15),
                    // 소셜 기능 (10점)
                    Map.entry("길드", 10),
                    Map.entry("guild", 10),
                    Map.entry("친구", 10),
                    Map.entry("friend", 10),
                    // 일반 기능 (5점)
                    Map.entry("튜토리얼", 5),
                    Map.entry("tutorial", 5),
                    Map.entry("설정", 5),
                    Map.entry("setting", 5));

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
     * 텍스트에서 매출 키워드 감지
     *
     * @param text 검색 대상 텍스트
     * @return 감지된 키워드 중 최대 점수
     */
    private int detectKeywords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        String lowerText = text.toLowerCase();
        int maxScore = 0;

        for (Map.Entry<String, Integer> entry : PAYMENT_KEYWORDS.entrySet()) {
            if (lowerText.contains(entry.getKey())) {
                maxScore = Math.max(maxScore, entry.getValue());
            }
        }

        return maxScore;
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

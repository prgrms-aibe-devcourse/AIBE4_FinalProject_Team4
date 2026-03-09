package kr.java.documind.domain.issue.service.severity.strategy;

import java.util.Map;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 게임 진행 차단 여부 기반 심각도 점수 계산 (0-20점)
 *
 * <p>점수 기준 (Developer Guide 기준):
 *
 * <ul>
 *   <li>완전 차단 (20점): 게임 시작 불가, 로그인 실패, 서버 접속 불가
 *   <li>메인 진행 차단 (15점): 메인 퀘스트 완료 불가, 필수 던전 입장 불가
 *   <li>부분 차단 (10점): 보상 수령 불가, 일부 기능 접근 불가
 *   <li>불편 (5점): 기능은 되지만 느림, UI 버그
 *   <li>차단 없음 (0점): 옵션 저장 실패, 통계 오류
 * </ul>
 */
@Slf4j
@Component
public class BlockingStrategy implements SeverityStrategy {

    /** 차단 키워드 → 점수 매핑 */
    private static final Map<String, Integer> BLOCKING_KEYWORDS =
            Map.ofEntries(
                    // 완전 차단 (20점)
                    Map.entry("로그인", 20),
                    Map.entry("login", 20),
                    Map.entry("접속", 20),
                    Map.entry("connect", 20),
                    Map.entry("서버", 20),
                    Map.entry("server", 20),
                    Map.entry("시작", 20),
                    Map.entry("start", 20),
                    Map.entry("authentication", 20),
                    // 메인 진행 차단 (15점)
                    Map.entry("메인", 15),
                    Map.entry("main", 15),
                    Map.entry("퀘스트", 15),
                    Map.entry("quest", 15),
                    Map.entry("던전", 15),
                    Map.entry("dungeon", 15),
                    Map.entry("입장", 15),
                    Map.entry("enter", 15),
                    // 부분 차단 (10점)
                    Map.entry("보상", 10),
                    Map.entry("reward", 10),
                    Map.entry("수령", 10),
                    Map.entry("claim", 10),
                    Map.entry("기능", 10),
                    Map.entry("feature", 10),
                    // 불편 (5점)
                    Map.entry("느림", 5),
                    Map.entry("slow", 5),
                    Map.entry("lag", 5),
                    Map.entry("ui", 5),
                    Map.entry("버그", 5),
                    Map.entry("bug", 5));

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
     * ErrorType에 따른 차단 점수
     *
     * @param errorType 에러 타입
     * @return 점수
     */
    private int getScoreByErrorType(ErrorType errorType) {
        return switch (errorType) {
            case AUTHENTICATION, AUTHORIZATION, NETWORK, DATABASE -> 20; // 완전 차단
            case TIMEOUT, DEADLOCK -> 15; // 메인 진행 차단
            case IO, SERIALIZATION -> 10; // 부분 차단
            default -> 0;
        };
    }

    /**
     * 텍스트에서 차단 키워드 감지
     *
     * @param text 검색 대상 텍스트
     * @return 감지된 키워드 중 최대 점수
     */
    private int detectBlockingKeywords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        String lowerText = text.toLowerCase();
        int maxScore = 0;

        for (Map.Entry<String, Integer> entry : BLOCKING_KEYWORDS.entrySet()) {
            if (lowerText.contains(entry.getKey())) {
                maxScore = Math.max(maxScore, entry.getValue());
            }
        }

        return maxScore;
    }

    @Override
    public SeverityFactor getFactor() {
        return SeverityFactor.BLOCKING;
    }

    @Override
    public String generateReason(int score, Issue issue, GameLog log) {
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

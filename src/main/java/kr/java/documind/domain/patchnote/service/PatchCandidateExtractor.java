package kr.java.documind.domain.patchnote.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.java.documind.domain.patchnote.model.dto.ChunkDiffResult;
import kr.java.documind.domain.patchnote.model.dto.PatchCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * diff 결과에서 패치노트 포함 후보를 추출하고 점수를 계산한다.
 *
 * <h3>점수 공식</h3>
 *
 * <pre>
 * score = 0.35 × deltaScore
 *       + 0.20 × playerVisibleScore
 *       + 0.15 × numericDeltaScore
 *       + 0.10 × actionabilityScore
 *       + 0.10 × releaseSpecificityScore
 *       − penalty (내부 개발 전용 내용)
 * </pre>
 *
 * <ul>
 *   <li>deltaScore — 변경량 (ADDED=0.8, MODIFIED=1.0−similarity)
 *   <li>playerVisibleScore — 플레이어 체감 키워드 포함 여부
 *   <li>numericDeltaScore — 현재↔이전 수치 패턴 변화 여부
 *   <li>actionabilityScore — 변경/추가/수정 등 행위 키워드 포함 여부
 *   <li>releaseSpecificityScore — 구체적 수치·화살표 표현 포함 여부
 * </ul>
 *
 * <p>최소 임계값({@value #MIN_SCORE}) 이상인 후보만 선택하며, 최대 {@value #MAX_CANDIDATES}건 반환한다.
 */
@Slf4j
@Component
public class PatchCandidateExtractor {

    static final double MIN_SCORE = 0.15;
    static final int MAX_CANDIDATES = 5;

    private static final Pattern NUMERIC_PATTERN =
            Pattern.compile(
                    "\\d+(?:[.,]\\d+)?\\s*(?:%|배|초|회|개|레벨)?\\s*"
                            + "(?:증가|감소|변경|조정|강화|너프|상승|하락|향상|저하)");

    private static final Pattern ARROW_PATTERN =
            Pattern.compile("\\d+(?:[.,]\\d+)?\\s*(?:→|->)\\s*\\d+(?:[.,]\\d+)?");

    private static final List<String> PLAYER_KEYWORDS =
            List.of(
                    "공격력", "방어력", "체력", "마나", "데미지", "보상", "확률", "레벨", "스킬", "쿨타임", "드랍", "드롭",
                    "상점", "로그인", "매칭", "캐릭터", "아이템", "퀘스트", "던전", "몬스터", "경험치", "골드", "밸런스", "버프",
                    "너프", "패치", "접속", "장비", "강화", "합성");

    private static final List<String> ACTION_KEYWORDS =
            List.of("변경", "추가", "수정", "삭제", "개선", "조정", "도입", "적용", "업데이트", "변화", "개편", "신규");

    /**
     * 패치노트 내부 개발 전용 패널티 키워드.
     *
     * <p>이 키워드가 포함된 청크는 점수를 0.15 감산한다.
     */
    private static final List<String> PENALTY_KEYWORDS =
            List.of("리팩토링", "마이그레이션", "ci/cd", "배포 스크립트", "코드 정리", "인덱스 최적화", "내부 개발", "테스트 코드");

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * diff 결과에서 패치 후보를 추출한다.
     *
     * <p>ADDED / MODIFIED 청크만 대상으로 하며, 점수 기준 내림차순으로 최대 {@value #MAX_CANDIDATES}건 반환한다.
     *
     * @param diffs {@link DocumentDiffService#computeDiff} 결과
     * @return 점수 내림차순 패치 후보 목록
     */
    public List<PatchCandidate> extract(List<ChunkDiffResult> diffs) {
        List<PatchCandidate> candidates =
                diffs.stream()
                        .filter(
                                d ->
                                        "ADDED".equals(d.changeType())
                                                || "MODIFIED".equals(d.changeType()))
                        .map(this::toCandidate)
                        .filter(c -> c.score() >= MIN_SCORE)
                        .sorted(Comparator.comparingDouble(PatchCandidate::score).reversed())
                        .limit(MAX_CANDIDATES)
                        .toList();

        log.debug(
                "[PatchCandidateExtractor] 후보 추출 완료 — 입력 diff {} 건 → 후보 {} 건",
                diffs.size(),
                candidates.size());

        return candidates;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    PatchCandidate toCandidate(ChunkDiffResult diff) {
        String current = diff.currentContent() != null ? diff.currentContent() : "";
        String previous = diff.previousContent() != null ? diff.previousContent() : "";

        // 1. 변경량 점수 — ADDED는 0.8 고정, MODIFIED는 유사도 거리
        double deltaScore = "ADDED".equals(diff.changeType()) ? 0.8 : (1.0 - diff.similarity());

        // 2. 플레이어 체감 키워드
        double playerScore = hasPlayerKeywords(current) ? 1.0 : 0.0;

        // 3. 수치 변화 감지
        double numericScore = hasNumericChange(current, previous) ? 1.0 : 0.0;

        // 4. 행위 키워드
        double actionScore = hasActionKeywords(current) ? 1.0 : 0.0;

        // 5. 구체성 (수치 또는 화살표 표현)
        double specificityScore = isSpecific(current) ? 1.0 : 0.0;

        double raw =
                0.35 * deltaScore
                        + 0.20 * playerScore
                        + 0.15 * numericScore
                        + 0.10 * actionScore
                        + 0.10 * specificityScore;

        double penalty = hasPenaltyKeywords(current) ? 0.15 : 0.0;
        double finalScore = Math.max(0.0, Math.min(1.0, raw - penalty));

        String evidence = buildEvidence(diff, current, previous);

        return new PatchCandidate(
                diff.chunkIndex(), current, previous, diff.changeType(), finalScore, evidence);
    }

    private boolean hasPlayerKeywords(String text) {
        String lower = text.toLowerCase();
        return PLAYER_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private boolean hasNumericChange(String current, String previous) {
        Set<String> currPatterns = extractNumericPatterns(current);
        Set<String> prevPatterns = extractNumericPatterns(previous);
        return !currPatterns.equals(prevPatterns);
    }

    private Set<String> extractNumericPatterns(String text) {
        Matcher m = NUMERIC_PATTERN.matcher(text);
        Set<String> set = new HashSet<>();
        while (m.find()) {
            set.add(m.group().trim().toLowerCase());
        }
        return set;
    }

    private boolean hasActionKeywords(String text) {
        String lower = text.toLowerCase();
        return ACTION_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private boolean isSpecific(String text) {
        return text.length() > 50
                && (NUMERIC_PATTERN.matcher(text).find()
                        || ARROW_PATTERN.matcher(text).find()
                        || text.contains("→")
                        || text.contains("->"));
    }

    private boolean hasPenaltyKeywords(String text) {
        String lower = text.toLowerCase();
        return PENALTY_KEYWORDS.stream().anyMatch(lower::contains);
    }

    /**
     * RAG 컨텍스트 삽입용 evidence 문자열 생성.
     *
     * <p>LLM이 변경 전·후를 명확히 인식할 수 있도록 구조화된 텍스트로 조립한다.
     */
    private String buildEvidence(ChunkDiffResult diff, String current, String previous) {
        StringBuilder sb = new StringBuilder();
        sb.append("[변경 유형: ").append(diff.changeType()).append("]\n");
        if (!previous.isBlank()) {
            sb.append("[이전]\n").append(previous.strip()).append("\n");
        }
        if (!current.isBlank()) {
            sb.append("[현재]\n").append(current.strip());
        }
        return sb.toString();
    }
}

package kr.java.documind.domain.patchnote.service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import kr.java.documind.domain.patchnote.model.dto.VectorChunkResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 패치노트 초안 생성을 위한 다중 신호 청크 재랭킹 서비스.
 *
 * <h3>점수 공식</h3>
 *
 * <pre>
 * score = base + roleBonus + numericBonus + playerBonus
 *       + actionBonus + deltaBonus
 *       - rolePenalty - supportPenalty - internalPenalty
 * </pre>
 *
 * <table>
 *   <tr><th>신호</th><th>가중치</th><th>조건</th></tr>
 *   <tr><td>base similarity</td><td>+0.10</td><td>항상 적용 (0~1 정규화)</td></tr>
 *   <tr><td>base rrf</td><td>+0.15</td><td>항상 적용 (0.033 기준 정규화)</td></tr>
 *   <tr><td>roleBonus — resolution / final_change / diff</td><td>+0.25</td><td>chunk_role 일치</td></tr>
 *   <tr><td>roleBonus — summary</td><td>+0.18</td><td>chunk_role = summary</td></tr>
 *   <tr><td>roleBonus — background_resolution</td><td>+0.12</td><td>chunk_role 일치</td></tr>
 *   <tr><td>roleBonus — background / comment</td><td>+0.05</td><td>chunk_role 일치</td></tr>
 *   <tr><td>numericBonus</td><td>+0.20</td><td>has_numeric_change = true</td></tr>
 *   <tr><td>playerBonus</td><td>+0.15</td><td>affects_player = true</td></tr>
 *   <tr><td>actionBonus</td><td>+0.10</td><td>컨텐츠에 액션 토큰 포함</td></tr>
 *   <tr><td>deltaBonus</td><td>+0.10</td><td>컨텐츠에 수치 변화 토큰 포함</td></tr>
 *   <tr><td>rolePenalty</td><td>−0.30</td><td>chunk_role이 정책/FAQ/가이드/지원</td></tr>
 *   <tr><td>supportPenalty</td><td>−0.15</td><td>컨텐츠에 지원 문의 토큰 포함</td></tr>
 *   <tr><td>internalPenalty</td><td>−0.10</td><td>chunk_role이 내부 문서 역할</td></tr>
 * </table>
 *
 * <p>최종 점수는 [0.0, 1.0]으로 클램핑된다.
 */
@Slf4j
@Service
public class PatchNoteReranker {

    // ─────────────────────────────────────────────────────────────────────────
    // 가중치 상수
    // ─────────────────────────────────────────────────────────────────────────

    /** RRF 점수 정규화 기준 (양쪽 rank=1일 때 최대값 ≈ 2/61 ≈ 0.033). */
    private static final double RRF_NORMALIZER = 0.033;

    private static final double W_SIMILARITY = 0.10;
    private static final double W_RRF = 0.15;

    private static final double BONUS_ROLE_RESOLUTION = 0.25;
    private static final double BONUS_ROLE_SUMMARY = 0.18;
    private static final double BONUS_ROLE_BG_RES = 0.12;
    private static final double BONUS_ROLE_BACKGROUND = 0.05;

    private static final double BONUS_NUMERIC = 0.20;
    private static final double BONUS_PLAYER = 0.15;
    private static final double BONUS_ACTION = 0.10;
    private static final double BONUS_DELTA = 0.10;
    private static final double PENALTY_NOT_PLAYER = 0.35;
    private static final double MIN_KEEP_SCORE = 0.18;

    private static final double PENALTY_ROLE_PENALIZED = 0.30;
    private static final double PENALTY_SUPPORT = 0.15;
    private static final double PENALTY_INTERNAL = 0.10;

    // ─────────────────────────────────────────────────────────────────────────
    // 토큰 집합
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 액션 토큰: 이번 릴리스에서 실제로 변경이 이뤄졌음을 시사하는 동사/명사.
     *
     * <p>패치노트 독자에게 직접적으로 전달되어야 하는 정보를 담은 청크를 우선시한다.
     */
    private static final Set<String> ACTION_TOKENS =
            Set.of(
                    "수정", "수정됨", "수정했", "수정하였", "변경", "변경됨", "변경했", "변경하였", "추가", "추가됨", "추가했",
                    "추가하였", "개선", "개선됨", "개선했", "적용", "적용됨", "적용했", "삭제", "삭제됨", "삭제했", "교체", "교체됨",
                    "교체했", "업데이트", "패치", "릴리스", "해결", "해결됨", "해결했", "수정완료");

    /**
     * 수치 변화 토큰: 구체적인 수치·비율·범위 변화를 나타내는 토큰.
     *
     * <p>플레이어 체감 밸런스 변화를 담은 청크를 우선시한다.
     */
    private static final Set<String> DELTA_TOKENS =
            Set.of(
                    "증가", "감소", "상승", "하락", "향상", "저하", "강화", "너프", "버프", "%", "퍼센트", "배", "배율",
                    "→", "에서", "로변경", "로조정", "초과", "이상", "이하", "미만");

    /**
     * 지원/문의 토큰: 고객 지원·FAQ 성격의 청크를 식별하는 토큰.
     *
     * <p>패치노트 초안에 부적합한 고객센터 안내·FAQ 성격 내용을 페널티 처리한다.
     */
    private static final Set<String> SUPPORT_TOKENS =
            Set.of(
                    "문의", "고객센터", "지원팀", "도움말", "이용약관", "환불", "결제문의", "1:1", "카카오채널", "전화상담",
                    "자주묻는", "자주하는", "FAQ");

    /** 정책/가이드 역할 청크 (패치노트 초안 기여도 낮음). */
    private static final Set<String> PENALIZED_ROLES =
            Set.of("policy", "faq", "guide", "troubleshooting", "support");

    /** 내부 문서 역할 청크 (외부 공개 부적합). */
    private static final Set<String> INTERNAL_ROLES =
            Set.of("internal", "private", "note", "memo", "draft_internal");

    public List<VectorChunkResult> rerank(List<VectorChunkResult> chunks) {
        return chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, score(chunk)))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .filter(sc -> sc.score() >= MIN_KEEP_SCORE)
                .map(ScoredChunk::chunk)
                .toList();
    }

    private record ScoredChunk(VectorChunkResult chunk, double score) {}

    double score(VectorChunkResult chunk) {
        double s = 0.0;

        s += W_SIMILARITY * clamp01(chunk.similarity());
        s += W_RRF * clamp01(chunk.rrfScore() / RRF_NORMALIZER);

        s += roleBonus(chunk.chunkRole());

        if (chunk.hasNumericChange()) s += BONUS_NUMERIC;
        if (chunk.affectsPlayer()) {
            s += BONUS_PLAYER;
        } else {
            s -= PENALTY_NOT_PLAYER;
        }

        String content = chunk.content() != null ? chunk.content() : "";
        if (containsAny(content, ACTION_TOKENS)) s += BONUS_ACTION;
        if (containsAny(content, DELTA_TOKENS)) s += BONUS_DELTA;

        if (chunk.chunkRole() != null && PENALIZED_ROLES.contains(chunk.chunkRole())) {
            s -= PENALTY_ROLE_PENALIZED;
        }
        if (chunk.chunkRole() != null && INTERNAL_ROLES.contains(chunk.chunkRole())) {
            s -= PENALTY_INTERNAL;
        }

        if (containsAny(content, SUPPORT_TOKENS)) s -= PENALTY_SUPPORT;

        return clamp01(s);
    }

    private double roleBonus(String chunkRole) {
        if (chunkRole == null) {
            return 0.0;
        }
        return switch (chunkRole) {
            case "resolution", "final_change", "diff" -> BONUS_ROLE_RESOLUTION;
            case "summary" -> BONUS_ROLE_SUMMARY;
            case "background_resolution" -> BONUS_ROLE_BG_RES;
            case "background", "comment" -> BONUS_ROLE_BACKGROUND;
            default -> 0.0;
        };
    }

    /** 컨텐츠에 토큰 집합 중 하나라도 포함되어 있는지 확인한다. */
    private boolean containsAny(String content, Set<String> tokens) {
        for (String token : tokens) {
            if (content.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

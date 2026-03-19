package kr.java.documind.domain.patchnote.service;

import java.util.List;
import kr.java.documind.domain.patchnote.config.TokenRagProperties;
import kr.java.documind.domain.patchnote.model.dto.ItemContext;
import kr.java.documind.domain.patchnote.model.dto.RagContext;
import kr.java.documind.domain.patchnote.model.dto.RagEvidence;
import kr.java.documind.domain.patchnote.model.dto.TokenEstimation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 컨텍스트 오버플로우 시 증거 블록을 단계적으로 제거하여 토큰 사용량을 축소하는 서비스.
 *
 * <h3>감소 전략 (역할 우선순위 기반)</h3>
 * <ol>
 *   <li>{@code chunk} 역할 증거 제거 — 가장 낮은 신호 강도
 *   <li>{@code background} 역할 증거 제거 — 배경 설명
 *   <li>{@code combined} 역할 증거 제거 — 배경+해결 병합
 *   <li>마지막 수단: 항목당 증거 1개 제한
 * </ol>
 *
 * <p>항상 보존되는 역할: {@code resolution}, {@code diff_change}, {@code summary}, {@code final_change}
 *
 * <h3>토큰 추정</h3>
 * {@code (summary.length + evidence.text.length) / 3 + promptOverhead}
 * — {@link TokenEstimator}와 동일한 한국어 기준 보수적 비율 사용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceReducer {

    /** 한국어 텍스트 기준 보수적 문자-토큰 비율. */
    private static final int CHARS_PER_TOKEN = 3;

    /**
     * 제거 우선순위 순서.
     *
     * <p>낮은 신호 역할부터 제거한다. 이 목록에 없는 역할({@code resolution}, {@code diff_change},
     * {@code summary}, {@code final_change})은 어떤 단계에서도 제거되지 않는다.
     */
    private static final List<String> DROP_PRIORITY = List.of(
            "chunk",      // 1순위 제거: 역할 미지정 문서 청크
            "background", // 2순위 제거: 배경 정보
            "combined"    // 3순위 제거: 배경+해결 병합 (배경 성분 포함)
    );

    private final TokenRagProperties properties;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 토큰 추정량이 한도를 초과하는 경우 증거 블록을 단계적으로 제거하여 반환한다.
     *
     * <p>한도 미초과 시 원본 {@link RagContext}를 그대로 반환한다 (새 객체 생성 없음).
     * 감소 과정은 항목별로 균등하게 적용되며, 특정 항목을 완전히 제거하지는 않는다.
     *
     * @param original 원본 RAG 컨텍스트
     * @return 토큰이 감소된 새 RagContext, 또는 감소 불필요 시 원본
     */
    public RagContext reduce(RagContext original) {
        int originalEstimate = estimateTokens(original.itemContexts());
        if (originalEstimate <= properties.tokenLimit()) {
            return original;
        }

        log.info(
                "컨텍스트 오버플로우 감소 시작 — 추정 토큰: {}, 한도: {}",
                originalEstimate, properties.tokenLimit());

        List<ItemContext> contexts = original.itemContexts();

        // 단계별 증거 제거
        for (String roleToDrop : DROP_PRIORITY) {
            if (estimateTokens(contexts) <= properties.tokenLimit()) {
                break;
            }
            List<ItemContext> reduced = dropByRole(contexts, roleToDrop);
            if (reduced != contexts) { // 실제로 무언가 제거된 경우에만 교체
                contexts = reduced;
                log.info(
                        "컨텍스트 감소 — '{}' 역할 증거 제거 완료, 추정 토큰: {}",
                        roleToDrop, estimateTokens(contexts));
            }
        }

        // 최후 수단: 항목당 증거 1개 제한
        if (estimateTokens(contexts) > properties.tokenLimit()) {
            contexts = limitPerItem(contexts, 1);
            log.info(
                    "컨텍스트 감소 — 항목당 1개 증거 제한 적용, 최종 추정 토큰: {}",
                    estimateTokens(contexts));
        }

        int reducedTokens = estimateTokens(contexts);
        log.info(
                "컨텍스트 오버플로우 감소 완료 — 원본: {}, 감소 후: {}, 한도: {}",
                originalEstimate, reducedTokens, properties.tokenLimit());

        TokenEstimation reducedEstimation = new TokenEstimation(
                reducedTokens,
                properties.tokenLimit(),
                reducedTokens > properties.tokenLimit(),
                original.tokenEstimation().itemCount());

        return new RagContext(
                contexts,
                original.sourceRefMap(),
                original.sourceRefs(),
                reducedEstimation);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@link ItemContext} 목록의 전체 추정 토큰 수를 계산한다.
     *
     * <p>summary 텍스트 + 모든 증거 텍스트의 총 길이를 {@value #CHARS_PER_TOKEN}으로 나누고
     * {@code promptOverhead}를 더한다.
     */
    private int estimateTokens(List<ItemContext> contexts) {
        int textChars = contexts.stream()
                .mapToInt(ic -> {
                    int summaryChars = ic.summary() != null ? ic.summary().length() : 0;
                    int evidenceChars = ic.evidences().stream()
                            .mapToInt(e -> e.text() != null ? e.text().length() : 0)
                            .sum();
                    return summaryChars + evidenceChars;
                })
                .sum();
        return properties.promptOverhead() + (textChars / CHARS_PER_TOKEN);
    }

    /**
     * 지정한 역할의 증거를 모든 항목에서 제거한다.
     *
     * <p>변경이 없는 경우 동일한 참조를 반환하여 불필요한 객체 생성을 피한다.
     */
    private List<ItemContext> dropByRole(List<ItemContext> contexts, String roleToDrop) {
        boolean anyChanged = false;
        List<ItemContext> result = new java.util.ArrayList<>(contexts.size());

        for (ItemContext ic : contexts) {
            List<RagEvidence> filtered = ic.evidences().stream()
                    .filter(e -> !roleToDrop.equals(e.role()))
                    .toList();

            if (filtered.size() == ic.evidences().size()) {
                result.add(ic); // 변경 없음
            } else {
                anyChanged = true;
                result.add(new ItemContext(
                        ic.ref(), ic.patchType(), ic.title(), ic.summary(),
                        filtered, ic.allowedSourceRefs()));
            }
        }

        return anyChanged ? List.copyOf(result) : contexts;
    }

    /**
     * 각 항목의 증거를 최대 {@code maxPerItem}개로 제한한다.
     *
     * <p>증거는 이미 {@link PatchNoteReranker} 점수 내림차순으로 정렬되어 있으므로,
     * 단순히 앞 N개를 유지하면 상위 신호 증거가 보존된다.
     */
    private List<ItemContext> limitPerItem(List<ItemContext> contexts, int maxPerItem) {
        return contexts.stream()
                .map(ic -> {
                    if (ic.evidences().size() <= maxPerItem) {
                        return ic;
                    }
                    List<RagEvidence> limited = ic.evidences().stream()
                            .limit(maxPerItem)
                            .toList();
                    return new ItemContext(
                            ic.ref(), ic.patchType(), ic.title(), ic.summary(),
                            limited, ic.allowedSourceRefs());
                })
                .toList();
    }
}

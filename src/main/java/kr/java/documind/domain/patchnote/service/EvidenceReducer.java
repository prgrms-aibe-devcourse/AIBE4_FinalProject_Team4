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

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceReducer {

    /** 한국어 텍스트 기준 보수적 문자-토큰 비율. */
    private static final int CHARS_PER_TOKEN = 3;

    private static final List<String> DROP_PRIORITY =
            List.of(
                    "chunk", // 1순위 제거: 역할 미지정 문서 청크
                    "background", // 2순위 제거: 배경 정보
                    "combined" // 3순위 제거: 배경+해결 병합 (배경 성분 포함)
                    );

    private final TokenRagProperties properties;

    public RagContext reduce(RagContext original) {
        int originalEstimate = estimateTokens(original.itemContexts());
        if (originalEstimate <= properties.tokenLimit()) {
            return original;
        }

        log.info("컨텍스트 오버플로우 감소 시작 — 추정 토큰: {}, 한도: {}", originalEstimate, properties.tokenLimit());

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
                        roleToDrop,
                        estimateTokens(contexts));
            }
        }

        // 최후 수단: 항목당 증거 1개 제한
        if (estimateTokens(contexts) > properties.tokenLimit()) {
            contexts = limitPerItem(contexts, 1);
            log.info("컨텍스트 감소 — 항목당 1개 증거 제한 적용, 최종 추정 토큰: {}", estimateTokens(contexts));
        }

        int reducedTokens = estimateTokens(contexts);
        log.info(
                "컨텍스트 오버플로우 감소 완료 — 원본: {}, 감소 후: {}, 한도: {}",
                originalEstimate,
                reducedTokens,
                properties.tokenLimit());

        TokenEstimation reducedEstimation =
                new TokenEstimation(
                        reducedTokens,
                        properties.tokenLimit(),
                        reducedTokens > properties.tokenLimit(),
                        original.tokenEstimation().itemCount());

        return new RagContext(
                contexts, original.sourceRefMap(), original.sourceRefs(), reducedEstimation);
    }

    private int estimateTokens(List<ItemContext> contexts) {
        int textChars =
                contexts.stream()
                        .mapToInt(
                                ic -> {
                                    int summaryChars =
                                            ic.summary() != null ? ic.summary().length() : 0;
                                    int evidenceChars =
                                            ic.evidences().stream()
                                                    .mapToInt(
                                                            e ->
                                                                    e.text() != null
                                                                            ? e.text().length()
                                                                            : 0)
                                                    .sum();
                                    return summaryChars + evidenceChars;
                                })
                        .sum();
        return properties.promptOverhead() + (textChars / CHARS_PER_TOKEN);
    }

    private List<ItemContext> dropByRole(List<ItemContext> contexts, String roleToDrop) {
        boolean anyChanged = false;
        List<ItemContext> result = new java.util.ArrayList<>(contexts.size());

        for (ItemContext ic : contexts) {
            List<RagEvidence> filtered =
                    ic.evidences().stream().filter(e -> !roleToDrop.equals(e.role())).toList();

            if (filtered.size() == ic.evidences().size()) {
                result.add(ic); // 변경 없음
            } else {
                anyChanged = true;
                result.add(
                        new ItemContext(
                                ic.ref(),
                                ic.patchType(),
                                ic.title(),
                                ic.summary(),
                                filtered,
                                ic.allowedSourceRefs()));
            }
        }

        return anyChanged ? List.copyOf(result) : contexts;
    }

    private List<ItemContext> limitPerItem(List<ItemContext> contexts, int maxPerItem) {
        return contexts.stream()
                .map(
                        ic -> {
                            if (ic.evidences().size() <= maxPerItem) {
                                return ic;
                            }
                            List<RagEvidence> limited =
                                    ic.evidences().stream().limit(maxPerItem).toList();
                            return new ItemContext(
                                    ic.ref(),
                                    ic.patchType(),
                                    ic.title(),
                                    ic.summary(),
                                    limited,
                                    ic.allowedSourceRefs());
                        })
                .toList();
    }
}

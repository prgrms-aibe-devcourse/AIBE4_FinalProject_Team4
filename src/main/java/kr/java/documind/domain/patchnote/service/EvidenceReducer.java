package kr.java.documind.domain.patchnote.service;

import java.util.Comparator;
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

    /**
     * 토큰 오버플로우 시 제거할 증거 역할 우선순위.
     *
     * <p>이전에는 {@code background}와 {@code combined}도 자동 제거 대상에 포함했으나, 실제 운영에서 다음 문제가 관찰되었다:
     *
     * <ul>
     *   <li>{@code background} — 이슈 배경 정보. LLM이 변경 맥락을 이해하는 데 핵심적이며, 제거 시 출력이 단편적이 됨.
     *   <li>{@code combined} — 배경+해결 병합 청크. 하나의 청크에 맥락과 해결 정보가 함께 담겨 있어 제거 시 정보 손실이 큼.
     * </ul>
     *
     * <p>따라서 자동 제거 대상을 역할 미지정 문서 청크({@code chunk})로만 한정한다. {@code chunk} 제거 후에도 토큰 한도를 초과하면
     * {@link #limitPerItem}의 항목당 최대 1개 제한으로 진행한다. 이 보수적 전략은 패치노트 내용의 완성도를 우선하고, 컨텍스트 윈도우 초과
     * 위험은 {@code tokenLimit} 설정으로 제어한다.
     */
    private static final List<String> DROP_PRIORITY =
            List.of(
                    "chunk" // 1순위(유일) 제거: 역할 미지정 문서 청크
                    // background — 이슈 맥락의 핵심. 제거 시 출력 품질 저하로 자동 제거 대상 제외.
                    // combined   — 맥락+해결 정보 병합 청크. 제거 시 정보 손실 과대로 자동 제거 대상 제외.
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
            .map(ic -> {
                if (ic.evidences().size() <= maxPerItem) {
                    return ic;
                }

                List<RagEvidence> limited =
                    ic.evidences().stream()
                        .sorted(
                            Comparator
                                // 1순위: 이번 릴리즈 관련
                                .comparing(RagEvidence::releaseSpecific).reversed()
                                // 2순위: 유저 체감
                                .thenComparing(RagEvidence::playerVisible).reversed()
                                // 3순위: 수치 변경 (밸런스 중요)
                                .thenComparing(RagEvidence::numericChange).reversed()
                                // 4순위: 유사도 점수
                                .thenComparing(RagEvidence::score).reversed()
                        )
                        .limit(maxPerItem)
                        .toList();

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

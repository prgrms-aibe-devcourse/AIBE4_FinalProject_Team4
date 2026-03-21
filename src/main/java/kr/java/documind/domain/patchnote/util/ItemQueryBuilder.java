package kr.java.documind.domain.patchnote.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import kr.java.documind.domain.archive.vector.infrastructure.EmbeddingModelClient;
import kr.java.documind.domain.patchnote.model.dto.ItemQuery;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.service.PatchNoteRagService;
import kr.java.documind.global.enums.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemQueryBuilder {

    /** 토큰 최대 보유 수. */
    private static final int MAX_TOKENS = 8;

    /** 토큰 분리 패턴 (공백, 구두점, 특수문자). */
    private static final Pattern TOKEN_SPLIT =
            Pattern.compile("[\\s\\p{Punct}·…「」『』【】〔〕\\[\\](){}]+");

    private static final Set<String> STOP_WORDS =
            Set.of(
                    // 조사
                    "이",
                    "가",
                    "을",
                    "를",
                    "은",
                    "는",
                    "에",
                    "에서",
                    "으로",
                    "로",
                    "와",
                    "과",
                    "의",
                    "도",
                    "만",
                    "까지",
                    "부터",
                    "이나",
                    "나",
                    "이며",
                    "며",
                    "이고",
                    "고",
                    "에게",
                    "한테",
                    "께",
                    "에게서",
                    "한테서",
                    "에서의",
                    "로의",
                    "으로의",
                    // 대명사·지시어
                    "이것",
                    "그것",
                    "저것",
                    "이거",
                    "그거",
                    "저거",
                    "여기",
                    "거기",
                    "저기",
                    "이런",
                    "그런",
                    "저런",
                    "이렇게",
                    "그렇게",
                    "저렇게",
                    "어떻게",
                    // 보조용언·동사 어간
                    "이다",
                    "하다",
                    "되다",
                    "있다",
                    "없다",
                    "같다",
                    "하여",
                    "해서",
                    "하는",
                    "하고",
                    "한다",
                    "됩니다",
                    "합니다",
                    "했습니다",
                    // 관계사·접속어
                    "그리고",
                    "그러나",
                    "하지만",
                    "따라서",
                    "그래서",
                    "즉",
                    "또한",
                    "또는",
                    "및",
                    "등",
                    "등의",
                    "등을",
                    "등이",
                    "기타",
                    // 명사형 의존어
                    "것",
                    "수",
                    "때",
                    "곳",
                    "경우",
                    "경우에",
                    "통해",
                    "대한",
                    "위한",
                    "관한",
                    "때문",
                    "따라",
                    "대해",
                    "위해",
                    "관해",
                    "관련",
                    "관련된",
                    // 수사·부사
                    "한",
                    "모든",
                    "각",
                    "전체",
                    "일부",
                    "다음",
                    "이전",
                    "현재",
                    "기존");

    private final EmbeddingModelClient embeddingModelClient;

    /**
     * 그룹별 대표 {@link ItemQuery}를 배치로 생성한다.
     *
     * <p>{@link PatchNoteRagService}의 그룹화 결과를 받아, 각 그룹의 모든 vector 항목(evidence=null인 항목)
     * title+summary를 공백으로 합산한 텍스트를 임베딩한다. 임베딩은 전체 그룹에 대해 한 번의 배치 호출로 처리하여 LLM API 비용을 최소화한다.
     *
     * <p>{@link ItemQuery#sourceId()}는 그룹 내 첫 번째 항목의 sourceId를 사용한다 — 그룹 내 모든 항목이 동일한 sourceId를
     * 공유하므로 안전하다.
     *
     * @param vectorGroups 그룹별 vector 항목 목록 (evidence=null인 항목만 포함, PatchNoteRagService가 분리)
     * @return 그룹별 {@link ItemQuery} 목록 (인덱스 순서 보장)
     */
    public List<ItemQuery> buildForGroups(List<List<PendingItem>> vectorGroups) {
        if (vectorGroups.isEmpty()) {
            return List.of();
        }

        // 그룹별 합산 텍스트 생성 — 배치 임베딩 입력
        List<String> embeddingTexts =
                vectorGroups.stream()
                        .map(
                                groupItems ->
                                        groupItems.stream()
                                                .map(
                                                        item -> {
                                                            String t =
                                                                    item.getTitle() != null
                                                                            ? item.getTitle()
                                                                            : "";
                                                            String s =
                                                                    item.getSummary() != null
                                                                            ? item.getSummary()
                                                                            : "";
                                                            return (t + " " + s).trim();
                                                        })
                                                .filter(text -> !text.isBlank())
                                                .collect(Collectors.joining(" ")))
                        .toList();

        List<float[]> embeddings = batchEmbed(embeddingTexts);

        List<ItemQuery> queries = new ArrayList<>(vectorGroups.size());
        for (int i = 0; i < vectorGroups.size(); i++) {
            List<PendingItem> groupItems = vectorGroups.get(i);
            PendingItem representative = groupItems.get(0); // sourceId 추출용 (그룹 내 동일)
            String combinedText = embeddingTexts.get(i);
            float[] embedding =
                    (embeddings != null && i < embeddings.size()) ? embeddings.get(i) : null;

            List<String> tokens = extractTokens(combinedText);
            String keyword = tokens.isEmpty() ? null : String.join(" ", tokens);
            String tsquery = tokens.isEmpty() ? null : String.join(" | ", tokens);

            // itemRef는 로깅용 — 그룹 대표 REF (첫 번째 항목 기반, changeIndex 미포함)
            String ref = buildGroupRef(representative);

            queries.add(
                    new ItemQuery(
                            representative.getSourceId(),
                            ref,
                            keyword,
                            tokens,
                            tsquery,
                            embedding));
        }

        log.debug(
                "그룹별 ItemQuery 빌드 완료 — groupCount={}, embeddingSuccess={}",
                vectorGroups.size(),
                embeddings != null);
        return queries;
    }

    /** 그룹 대표 REF 생성 — changeIndex 없는 형식. 로깅 전용. */
    private String buildGroupRef(PendingItem representative) {
        if (representative.getSourceType() == SourceType.ISSUE) {
            return "ISSUE-" + representative.getSourceId();
        }
        return "DOC-" + representative.getSourceId();
    }

    public List<ItemQuery> buildAll(List<PendingItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }

        // 임베딩 배치: 전체 텍스트를 한 번에 임베딩
        List<String> embeddingTexts =
                items.stream().map(item -> item.getTitle() + " " + item.getSummary()).toList();

        List<float[]> embeddings = batchEmbed(embeddingTexts);

        List<ItemQuery> queries = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            float[] embedding =
                    (embeddings != null && i < embeddings.size()) ? embeddings.get(i) : null;
            queries.add(buildSingle(items.get(i), embedding));
        }

        log.debug(
                "ItemQuery 빌드 완료 — itemCount={}, embeddingSuccess={}",
                items.size(),
                embeddings != null);
        return queries;
    }

    private ItemQuery buildSingle(PendingItem item, float[] embedding) {
        String text = item.getTitle() + " " + item.getSummary();
        List<String> tokens = extractTokens(text);

        String keyword = tokens.isEmpty() ? null : String.join(" ", tokens);
        String tsquery = tokens.isEmpty() ? null : String.join(" | ", tokens);
        String ref = buildRef(item);

        return new ItemQuery(item.getSourceId(), ref, keyword, tokens, tsquery, embedding);
    }

    private List<String> extractTokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        for (String token : TOKEN_SPLIT.split(text.strip())) {
            if (token.length() < 2) {
                continue;
            }
            if (STOP_WORDS.contains(token)) {
                continue;
            }
            if (!tokens.contains(token)) { // 중복 제거 (순서 유지)
                tokens.add(token);
                if (tokens.size() >= MAX_TOKENS) {
                    break;
                }
            }
        }
        return List.copyOf(tokens);
    }

    private List<float[]> batchEmbed(List<String> texts) {
        try {
            return embeddingModelClient.embed(texts);
        } catch (Exception e) {
            log.warn("ItemQueryBuilder 배치 임베딩 실패 — 키워드 전용 검색으로 fallback. error={}", e.getMessage());
            return null;
        }
    }

    private String buildRef(PendingItem item) {
        if (item.getSourceType() == SourceType.ISSUE) {
            return "ISSUE-" + item.getSourceId();
        }
        return "DOC-" + item.getSourceId() + "-" + item.getChangeIndex();
    }
}

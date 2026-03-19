package kr.java.documind.domain.patchnote.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import kr.java.documind.domain.archive.vector.infrastructure.EmbeddingModelClient;
import kr.java.documind.domain.patchnote.model.dto.ItemQuery;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
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

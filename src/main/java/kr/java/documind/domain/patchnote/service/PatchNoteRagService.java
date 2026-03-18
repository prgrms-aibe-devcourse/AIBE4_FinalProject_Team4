package kr.java.documind.domain.patchnote.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.java.documind.domain.archive.vector.infrastructure.EmbeddingModelClient;
import kr.java.documind.domain.patchnote.infrastructure.HybridVectorSearchRepository;
import kr.java.documind.domain.patchnote.model.dto.RagContext;
import kr.java.documind.domain.patchnote.model.dto.TokenEstimation;
import kr.java.documind.domain.patchnote.model.dto.VectorChunkResult;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.util.TokenEstimator;
import kr.java.documind.global.enums.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 패치노트 초안 생성을 위한 RAG 컨텍스트 빌더.
 *
 * <p>처리 순서:
 * <ol>
 *   <li>원본 삭제 항목({@code sourceDeleted=true}) 제외
 *   <li>소스 ID 목록 추출
 *   <li>요약 기반 키워드 생성 + 임베딩 (best-effort — 실패 시 키워드 전용 검색)
 *   <li>{@link HybridVectorSearchRepository}로 청크 검색
 *   <li>소스별 재랭킹: {@code has_numeric_change} → {@code affects_player} → {@code chunk_role}
 *       → 유사도 순
 *   <li>소스별 상위 N 청크 선택
 *   <li>patch_type 그룹별 컨텍스트 조립
 *   <li>소스 REF 매핑 (ISSUE-{sourceId}, DOC-{sourceId})
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatchNoteRagService {

    /** 소스 하나당 최대 포함 청크 수. */
    private static final int MAX_CHUNKS_PER_SOURCE = 3;

    /** 하이브리드 서치 총 후보 수. */
    private static final int TOTAL_CHUNK_LIMIT = 60;

    /** 키워드 임베딩 쿼리 최대 길이 (chars). */
    private static final int MAX_KEYWORD_LENGTH = 500;

    /** 청크 컨텐츠 최대 표시 길이 (chars). */
    private static final int MAX_CONTENT_LENGTH = 800;

    private final HybridVectorSearchRepository hybridVectorSearchRepository;
    private final EmbeddingModelClient embeddingModelClient;
    private final TokenEstimator tokenEstimator;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * RAG 컨텍스트 빌드.
     *
     * @param projectId    프로젝트 UUID
     * @param pendingItems 초안에 포함할 PendingItem 목록
     * @return 조립된 RAG 컨텍스트 (소스 없으면 {@link RagContext#empty(TokenEstimation)} 반환)
     */
    public RagContext buildContext(UUID projectId, List<PendingItem> pendingItems) {

        // 1. 원본 삭제 항목 제외
        List<PendingItem> activeItems =
                pendingItems.stream().filter(item -> !item.isSourceDeleted()).toList();

        TokenEstimation tokenEstimation = tokenEstimator.estimate(activeItems);

        if (activeItems.isEmpty()) {
            log.debug("RAG 컨텍스트 빌드 스킵 — 활성 소스 없음 (projectId={})", projectId);
            return RagContext.empty(tokenEstimation);
        }

        // 2. 소스 ID 목록
        List<Long> sourceIds = activeItems.stream().map(PendingItem::getSourceId).toList();

        // 3. 키워드 생성 + 임베딩 (best-effort)
        String keyword = buildKeyword(activeItems);
        float[] queryVector = embedKeyword(keyword);

        // 4. 하이브리드 서치
        List<VectorChunkResult> allChunks =
                hybridVectorSearchRepository.hybridSearch(
                        projectId.toString(), sourceIds, keyword, queryVector, TOTAL_CHUNK_LIMIT);

        log.debug(
                "하이브리드 서치 완료 — projectId={}, sourceCount={}, chunkCount={}",
                projectId, activeItems.size(), allChunks.size());

        // 5. 소스별 그룹핑
        Map<Long, List<VectorChunkResult>> chunksBySource =
                allChunks.stream().collect(Collectors.groupingBy(VectorChunkResult::sourceId));

        // 6. 소스별 재랭킹 + 상위 N 청크 선택
        Map<Long, List<VectorChunkResult>> topChunksMap = new LinkedHashMap<>();
        for (PendingItem item : activeItems) {
            List<VectorChunkResult> chunks =
                    chunksBySource.getOrDefault(item.getSourceId(), List.of());
            topChunksMap.put(
                    item.getSourceId(),
                    rerank(chunks).stream().limit(MAX_CHUNKS_PER_SOURCE).toList());
        }

        // 7. 소스 REF 매핑
        Map<String, String> sourceRefMap = buildSourceRefMap(activeItems);

        // 8. 컨텍스트 조립
        String contextText = assembleContext(activeItems, topChunksMap, sourceRefMap);

        return new RagContext(
                contextText, sourceRefMap, List.copyOf(sourceRefMap.keySet()), tokenEstimation);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PendingItem 요약을 합쳐 검색 키워드를 만든다.
     *
     * <p>pg_bigm LIKE 쿼리에 사용되므로 최대 길이를 제한한다.
     */
    private String buildKeyword(List<PendingItem> items) {
        String combined =
                items.stream()
                        .map(item -> item.getTitle() + " " + item.getSummary())
                        .collect(Collectors.joining(" "));

        return combined.length() > MAX_KEYWORD_LENGTH
                ? combined.substring(0, MAX_KEYWORD_LENGTH)
                : combined;
    }

    /**
     * 키워드를 임베딩 벡터로 변환한다.
     *
     * <p>임베딩 API 호출 실패 시 null을 반환하여 키워드 전용 검색으로 fallback한다.
     */
    private float[] embedKeyword(String keyword) {
        try {
            List<float[]> embeddings = embeddingModelClient.embed(List.of(keyword));
            return embeddings.isEmpty() ? null : embeddings.get(0);
        } catch (Exception e) {
            log.warn("RAG 키워드 임베딩 실패 — 키워드 전용 검색으로 fallback. error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 청크 재랭킹.
     *
     * <p>우선순위 (내림차순):
     * <ol>
     *   <li>{@code has_numeric_change} — 수치 변경 포함 청크 우선
     *   <li>{@code affects_player} — 플레이어 직접 영향 청크 우선
     *   <li>{@code chunk_role} 우선순위 — background_resolution > resolution > background > 기타
     *   <li>벡터 유사도 — 높을수록 우선
     * </ol>
     *
     * <p>각 조건을 독립 역순으로 정렬하기 위해 정수 매핑 방식을 사용한다.
     * ({@code Comparator.reversed()}는 전체 체인을 뒤집으므로 사용하지 않는다.)
     */
    private List<VectorChunkResult> rerank(List<VectorChunkResult> chunks) {
        return chunks.stream()
                .sorted(
                        // has_numeric_change DESC: true(0) → false(1)
                        Comparator.<VectorChunkResult>comparingInt(
                                        chunk -> chunk.hasNumericChange() ? 0 : 1)
                                // affects_player DESC: true(0) → false(1)
                                .thenComparingInt(chunk -> chunk.affectsPlayer() ? 0 : 1)
                                // chunkRolePriority DESC: 부호 반전으로 오름차순 = 원값 내림차순
                                .thenComparingInt(chunk -> -chunk.chunkRolePriority())
                                // similarity DESC: 부호 반전으로 오름차순 = 원값 내림차순
                                .thenComparingDouble(chunk -> -chunk.similarity()))
                .toList();
    }

    /**
     * 소스 REF 매핑 생성.
     *
     * <ul>
     *   <li>ISSUE → {@code ISSUE-{sourceId}}
     *   <li>DOCUMENT → {@code DOC-{sourceId}}
     * </ul>
     */
    private Map<String, String> buildSourceRefMap(List<PendingItem> items) {
        Map<String, String> map = new LinkedHashMap<>();
        for (PendingItem item : items) {
            map.put(buildRef(item), item.getTitle());
        }
        return map;
    }

    private String buildRef(PendingItem item) {
        return item.getSourceType() == SourceType.ISSUE
                ? "ISSUE-" + item.getSourceId()
                : "DOC-" + item.getSourceId();
    }

    /**
     * LLM 시스템 프롬프트에 삽입할 컨텍스트 텍스트 조립.
     *
     * <p>{@code patch_type} 별로 섹션을 구분하고, 각 소스의 요약과 청크 상세를 포함한다.
     */
    private String assembleContext(
            List<PendingItem> items,
            Map<Long, List<VectorChunkResult>> topChunksMap,
            Map<String, String> sourceRefMap) {

        StringBuilder sb = new StringBuilder();

        // patch_type 그룹핑 (PatchType 열거 순서 유지)
        Map<PatchType, List<PendingItem>> byType =
                items.stream().collect(Collectors.groupingBy(PendingItem::getPatchType));

        for (PatchType type : PatchType.values()) {
            List<PendingItem> typeItems = byType.getOrDefault(type, List.of());
            if (typeItems.isEmpty()) {
                continue;
            }

            sb.append("### 분류: ").append(type.name()).append("\n\n");

            for (PendingItem item : typeItems) {
                String ref = buildRef(item);
                sb.append("#### ").append(ref).append(" — ").append(item.getTitle()).append('\n');
                sb.append("요약: ").append(item.getSummary()).append('\n');

                List<VectorChunkResult> chunks =
                        topChunksMap.getOrDefault(item.getSourceId(), List.of());
                for (VectorChunkResult chunk : chunks) {
                    String content = truncateContent(chunk.content());
                    sb.append("상세 내용: ").append(content).append('\n');
                }
                sb.append('\n');
            }
        }

        return sb.toString().strip();
    }

    /** 청크 컨텐츠를 최대 길이로 잘라 반환. */
    private String truncateContent(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > MAX_CONTENT_LENGTH
                ? content.substring(0, MAX_CONTENT_LENGTH) + "..."
                : content;
    }
}

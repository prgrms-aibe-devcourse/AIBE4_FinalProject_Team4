package kr.java.documind.domain.patchnote.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.dto.ItemContext;
import kr.java.documind.domain.patchnote.model.dto.ItemQuery;
import kr.java.documind.domain.patchnote.model.dto.RagContext;
import kr.java.documind.domain.patchnote.model.dto.RagEvidence;
import kr.java.documind.domain.patchnote.model.dto.TokenEstimation;
import kr.java.documind.domain.patchnote.model.dto.VectorChunkResult;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.repository.HybridVectorSearchRepositoryCustom;
import kr.java.documind.domain.patchnote.util.TokenEstimator;
import kr.java.documind.global.enums.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 패치노트 초안 생성을 위한 RAG 컨텍스트 빌더.
 *
 * <h3>처리 순서</h3>
 *
 * <ol>
 *   <li>원본 삭제 항목({@code sourceDeleted=true}) 제외
 *   <li>evidence 보유 여부로 항목 분리 — evidence 항목: diff 텍스트 직접 사용 (벡터 검색 불필요) — vector 항목: 하이브리드 벡터 검색 수행
 *   <li>vector 항목: {@link ItemQueryBuilder}로 항목별 독립 {@link ItemQuery} 생성 (배치 임베딩으로 API 호출 최소화)
 *   <li>항목별 {@link HybridVectorSearchRepositoryCustom#searchForItem} 호출 — source_id 스코프 제한으로 검색 노이즈
 *       최소화
 *   <li>항목별 {@link PatchNoteReranker} 재랭킹 — 다중 신호 점수 기반
 *   <li>항목별 상위 N 청크 선택
 *   <li>항목별 {@link ItemContext} 조립 — evidence 항목: {@code diff_change} RagEvidence — vector 항목: 청크
 *       기반 RagEvidence 목록
 *   <li>소스 REF 매핑 (ISSUE-{sourceId}, DOC-{sourceId}-{changeIndex})
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatchNoteRagService {

    /** 소스 하나당 최대 포함 청크 수. */
    private static final int MAX_CHUNKS_PER_SOURCE = 3;

    /** 항목별 하이브리드 서치 후보 수. */
    private static final int PER_ITEM_CHUNK_LIMIT = 12;

    /** 청크 컨텐츠 최대 표시 길이 (chars). */
    private static final int MAX_CONTENT_LENGTH = 800;

    private final HybridVectorSearchRepositoryCustom hybridVectorSearchRepositoryCustom;
    private final ItemQueryBuilder itemQueryBuilder;
    private final PatchNoteReranker patchNoteReranker;
    private final TokenEstimator tokenEstimator;

    public RagContext buildContext(UUID projectId, List<PendingItem> pendingItems) {

        // 1. 원본 삭제 항목 제외
        List<PendingItem> activeItems =
                pendingItems.stream().filter(item -> !item.isSourceDeleted()).toList();

        TokenEstimation tokenEstimation = tokenEstimator.estimate(activeItems);

        if (activeItems.isEmpty()) {
            log.debug("RAG 컨텍스트 빌드 스킵 — 활성 소스 없음 (projectId={})", projectId);
            return RagContext.empty(tokenEstimation);
        }

        // 2. evidence 보유 여부로 항목 분리
        //    - evidenceItems: diff 기반 문서 변경 → RagEvidence(diff_change) 직접 생성
        //    - vectorItems:   이슈 / 신규 문서   → 항목별 하이브리드 벡터 검색
        List<PendingItem> evidenceItems =
                activeItems.stream().filter(item -> item.getEvidence() != null).toList();
        List<PendingItem> vectorItems =
                activeItems.stream().filter(item -> item.getEvidence() == null).toList();

        // key: PendingItem.id → 청크 목록 (evidence 항목은 빈 목록)
        Map<Long, List<VectorChunkResult>> topChunksById = new LinkedHashMap<>();

        for (PendingItem item : evidenceItems) {
            topChunksById.put(item.getId(), List.of());
        }

        if (!vectorItems.isEmpty()) {
            // 3. 항목별 ItemQuery 생성 (배치 임베딩)
            List<ItemQuery> itemQueries = itemQueryBuilder.buildAll(vectorItems);

            // 4–6. 항목별 검색 → 재랭킹 → 상위 N 선택
            for (int i = 0; i < vectorItems.size(); i++) {
                PendingItem item = vectorItems.get(i);
                ItemQuery query = itemQueries.get(i);

                List<VectorChunkResult> chunks =
                        hybridVectorSearchRepositoryCustom.searchForItem(
                                projectId.toString(), query, PER_ITEM_CHUNK_LIMIT);

                List<VectorChunkResult> topChunks =
                        patchNoteReranker.rerank(chunks).stream()
                                .limit(MAX_CHUNKS_PER_SOURCE)
                                .toList();

                topChunksById.put(item.getId(), topChunks);

                log.debug(
                        "항목별 서치 완료 — ref={}, sourceId={}, rawChunks={}, topChunks={}",
                        query.itemRef(),
                        query.sourceId(),
                        chunks.size(),
                        topChunks.size());
            }
        }

        // 7. 소스 REF 매핑 (항목별 고유 REF 보장)
        Map<String, String> sourceRefMap = buildSourceRefMap(activeItems);

        // 8. 항목별 ItemContext 조립 (구조화된 증거 블록)
        List<ItemContext> itemContexts =
                buildItemContexts(activeItems, topChunksById, sourceRefMap);

        return new RagContext(
                itemContexts, sourceRefMap, List.copyOf(sourceRefMap.keySet()), tokenEstimation);
    }

    private Map<String, String> buildSourceRefMap(List<PendingItem> items) {
        Map<String, String> map = new LinkedHashMap<>();
        for (PendingItem item : items) {
            map.put(buildRef(item), item.getTitle());
        }
        return map;
    }

    private String buildRef(PendingItem item) {
        if (item.getSourceType() == SourceType.ISSUE) {
            return "ISSUE-" + item.getSourceId();
        }
        return "DOC-" + item.getSourceId() + "-" + item.getChangeIndex();
    }

    private List<ItemContext> buildItemContexts(
            List<PendingItem> items,
            Map<Long, List<VectorChunkResult>> topChunksById,
            Map<String, String> sourceRefMap) {

        List<ItemContext> result = new ArrayList<>(items.size());

        for (PendingItem item : items) {
            String ref = buildRef(item);
            List<RagEvidence> evidences = buildEvidences(item, ref, topChunksById);

            result.add(
                    new ItemContext(
                            ref,
                            item.getPatchType(),
                            item.getTitle(),
                            item.getSummary(),
                            evidences,
                            List.of(ref) // allowedSourceRefs: 현재는 자신의 REF만 허용
                            ));
        }

        return result;
    }

    private List<RagEvidence> buildEvidences(
            PendingItem item, String ref, Map<Long, List<VectorChunkResult>> topChunksById) {

        if (item.getEvidence() != null) {
            // diff 기반 항목: evidence 텍스트 자체가 증거
            double score = item.getScore() != null ? item.getScore() : 1.0;
            return List.of(
                    new RagEvidence(
                            ref,
                            "diff_change",
                            truncateContent(item.getEvidence()),
                            score,
                            true, // playerVisible: diff 항목은 플레이어 영향 기본 가정
                            false, // numericChange: 항목 레벨에서 미추적
                            true // releaseSpecific: diff 항목은 항상 이번 릴리스 변경사항
                            ));
        }

        // 벡터 검색 항목: 재랭킹된 청크를 RagEvidence로 변환
        List<VectorChunkResult> chunks = topChunksById.getOrDefault(item.getId(), List.of());
        return chunks.stream()
                .map(
                        chunk ->
                                new RagEvidence(
                                        ref,
                                        resolveChunkRole(chunk.chunkRole()),
                                        truncateContent(chunk.content()),
                                        chunk.similarity(),
                                        chunk.affectsPlayer(),
                                        chunk.hasNumericChange(),
                                        chunk.hasNumericChange() || chunk.affectsPlayer()))
                .toList();
    }

    private String resolveChunkRole(String chunkRole) {
        if (chunkRole == null) {
            return "chunk";
        }
        return switch (chunkRole) {
            case "background_resolution" -> "combined";
            case "resolution", "final_change", "diff" -> "resolution";
            case "background" -> "background";
            case "summary" -> "summary";
            default -> "chunk";
        };
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

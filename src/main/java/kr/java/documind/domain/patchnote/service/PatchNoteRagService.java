package kr.java.documind.domain.patchnote.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.java.documind.domain.patchnote.model.dto.ItemContext;
import kr.java.documind.domain.patchnote.model.dto.ItemQuery;
import kr.java.documind.domain.patchnote.model.dto.RagContext;
import kr.java.documind.domain.patchnote.model.dto.RagEvidence;
import kr.java.documind.domain.patchnote.model.dto.TokenEstimation;
import kr.java.documind.domain.patchnote.model.dto.VectorChunkResult;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.repository.HybridVectorSearchRepositoryCustom;
import kr.java.documind.domain.patchnote.util.ItemQueryBuilder;
import kr.java.documind.domain.patchnote.util.TokenEstimator;
import kr.java.documind.global.enums.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatchNoteRagService {

    private static final int MAX_CHUNKS_PER_SOURCE = 5;
    private static final int ISSUE_CHUNK_LIMIT = 12;
    private static final int DOC_CHUNK_LIMIT = 6;
    private static final int EVIDENCE_SUFFICIENT_THRESHOLD = 2;
    private static final int MAX_CONTENT_LENGTH = 800;

    private final HybridVectorSearchRepositoryCustom hybridVectorSearchRepositoryCustom;
    private final ItemQueryBuilder itemQueryBuilder;
    private final PatchNoteReranker patchNoteReranker;
    private final TokenEstimator tokenEstimator;

    private record GroupKey(SourceType sourceType, Long sourceId) {}

    public RagContext buildContext(UUID projectId, List<PendingItem> pendingItems) {
        List<PendingItem> activeItems =
                pendingItems.stream().filter(item -> !item.isSourceDeleted()).toList();

        TokenEstimation tokenEstimation = tokenEstimator.estimate(activeItems);

        if (activeItems.isEmpty()) {
            log.debug("RAG 컨텍스트 빌드 스킵 — 활성 소스 없음 (projectId={})", projectId);
            return RagContext.empty(tokenEstimation);
        }

        Map<GroupKey, List<PendingItem>> grouped = groupBySource(activeItems);
        Map<GroupKey, List<VectorChunkResult>> topChunksByGroup =
                resolveVectorChunks(projectId, grouped);
        Map<String, String> sourceRefMap = buildSourceRefMap(grouped);
        List<ItemContext> itemContexts = buildItemContexts(grouped, topChunksByGroup);

        return new RagContext(
                itemContexts, sourceRefMap, List.copyOf(sourceRefMap.keySet()), tokenEstimation);
    }

    private Map<GroupKey, List<PendingItem>> groupBySource(List<PendingItem> items) {
        Map<GroupKey, List<PendingItem>> grouped = new LinkedHashMap<>();
        for (PendingItem item : items) {
            GroupKey key = new GroupKey(item.getSourceType(), item.getSourceId());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private Map<GroupKey, List<VectorChunkResult>> resolveVectorChunks(
            UUID projectId, Map<GroupKey, List<PendingItem>> grouped) {

        Map<GroupKey, List<VectorChunkResult>> topChunksByGroup = new LinkedHashMap<>();
        List<GroupKey> vectorGroupKeys = new ArrayList<>();
        List<List<PendingItem>> vectorGroupItems = new ArrayList<>();

        for (Map.Entry<GroupKey, List<PendingItem>> entry : grouped.entrySet()) {
            GroupKey key = entry.getKey();
            List<PendingItem> allGroupItems = entry.getValue();
            List<PendingItem> vectorItems =
                    allGroupItems.stream().filter(item -> item.getEvidence() == null).toList();

            topChunksByGroup.put(key, List.of());

            if (vectorItems.isEmpty()) {
                continue;
            }

            if (key.sourceType() == SourceType.DOCUMENT) {
                long evidenceCount =
                        allGroupItems.stream().filter(item -> item.getEvidence() != null).count();
                if (evidenceCount >= EVIDENCE_SUFFICIENT_THRESHOLD) {
                    log.debug(
                            "문서 그룹 벡터 검색 생략 — sourceId={}, evidenceCount={}개",
                            key.sourceId(),
                            evidenceCount);
                    continue;
                }
            }

            vectorGroupKeys.add(key);
            vectorGroupItems.add(vectorItems);
        }

        if (vectorGroupKeys.isEmpty()) {
            return topChunksByGroup;
        }

        List<ItemQuery> groupQueries = itemQueryBuilder.buildForGroups(vectorGroupItems);

        for (int i = 0; i < vectorGroupKeys.size(); i++) {
            GroupKey key = vectorGroupKeys.get(i);
            ItemQuery query = groupQueries.get(i);

            int chunkLimit =
                    key.sourceType() == SourceType.ISSUE ? ISSUE_CHUNK_LIMIT : DOC_CHUNK_LIMIT;

            List<VectorChunkResult> chunks =
                    hybridVectorSearchRepositoryCustom.searchForItem(
                            projectId.toString(), query, chunkLimit);

            List<VectorChunkResult> topChunks =
                    patchNoteReranker.rerank(chunks).stream().limit(MAX_CHUNKS_PER_SOURCE).toList();

            topChunksByGroup.put(key, topChunks);
        }

        return topChunksByGroup;
    }

    private String buildGroupRef(GroupKey key) {
        if (key.sourceType() == SourceType.ISSUE) {
            return "ISSUE-" + key.sourceId();
        }
        return "DOC-" + key.sourceId();
    }

    private Map<String, String> buildSourceRefMap(Map<GroupKey, List<PendingItem>> grouped) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<GroupKey, List<PendingItem>> entry : grouped.entrySet()) {
            String ref = buildGroupRef(entry.getKey());
            map.put(ref, resolveRepresentativeTitle(entry.getValue()));
        }
        return map;
    }

    private List<ItemContext> buildItemContexts(
            Map<GroupKey, List<PendingItem>> grouped,
            Map<GroupKey, List<VectorChunkResult>> topChunksByGroup) {

        List<ItemContext> result = new ArrayList<>(grouped.size());

        for (Map.Entry<GroupKey, List<PendingItem>> entry : grouped.entrySet()) {
            GroupKey key = entry.getKey();
            List<PendingItem> groupItems = entry.getValue();
            String ref = buildGroupRef(key);

            String title = resolveRepresentativeTitle(groupItems);
            PatchType patchType = resolveGroupPatchType(groupItems);
            String summary = mergeGroupSummary(groupItems);

            List<VectorChunkResult> topChunks = topChunksByGroup.getOrDefault(key, List.of());
            List<RagEvidence> evidences = buildGroupEvidences(groupItems, ref, topChunks);

            if (evidences.isEmpty() && (summary == null || summary.isBlank())) {
                log.debug("증거/요약 없는 그룹 생략 — ref={}", ref);
                continue;
            }

            result.add(new ItemContext(ref, patchType, title, summary, evidences, List.of(ref)));
        }

        return result;
    }

    private PatchType resolveGroupPatchType(List<PendingItem> groupItems) {
        Map<PatchType, Long> counts =
                groupItems.stream()
                        .map(PendingItem::getPatchType)
                        .filter(Objects::nonNull)
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        if (counts.isEmpty()) {
            return groupItems.get(0).getPatchType();
        }

        long maxCount = counts.values().stream().mapToLong(Long::longValue).max().orElse(0L);

        Set<PatchType> topTypes =
                counts.entrySet().stream()
                        .filter(e -> e.getValue() == maxCount)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());

        for (PatchType priority :
                List.of(PatchType.FIX, PatchType.NEW, PatchType.CHANGE, PatchType.MAINTENANCE)) {
            if (topTypes.contains(priority)) {
                return priority;
            }
        }
        return groupItems.get(0).getPatchType();
    }

    private String resolveRepresentativeTitle(List<PendingItem> groupItems) {
        return groupItems.stream()
                .map(PendingItem::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .max(Comparator.comparingInt(String::length))
                .orElse(groupItems.get(0).getTitle());
    }

    /** 기존 개행 결합 대신, 모델이 "리스트"가 아니라 "한 묶음의 변화"로 이해하도록 서술형 힌트 문자열로 합친다. */
    private String mergeGroupSummary(List<PendingItem> groupItems) {
        List<String> summaries =
                groupItems.stream()
                        .map(PendingItem::getSummary)
                        .filter(s -> s != null && !s.isBlank())
                        .map(s -> s.strip().replaceAll("\\s+", " "))
                        .distinct()
                        .toList();

        if (summaries.isEmpty()) {
            return "";
        }
        if (summaries.size() == 1) {
            return summaries.get(0);
        }

        // 개행 대신 서술형 연결
        return "이 소스에는 다음과 같은 관련 변경이 포함됩니다: " + String.join(" / ", summaries);
    }

    private List<RagEvidence> buildGroupEvidences(
            List<PendingItem> groupItems, String ref, List<VectorChunkResult> topChunks) {

        List<RagEvidence> evidences = new ArrayList<>();

        for (PendingItem item : groupItems) {
            if (item.getEvidence() != null) {
                double score = item.getScore() != null ? item.getScore() : 1.0;
                evidences.add(
                        new RagEvidence(
                                ref,
                                "diff_change",
                                truncateContent(item.getEvidence()),
                                score,
                                true,
                                false,
                                true));
            }
        }

        for (VectorChunkResult chunk : topChunks) {
            evidences.add(
                    new RagEvidence(
                            ref,
                            resolveChunkRole(chunk.chunkRole()),
                            truncateContent(chunk.content()),
                            chunk.similarity(),
                            chunk.affectsPlayer(),
                            chunk.hasNumericChange(),
                            chunk.hasNumericChange() || chunk.affectsPlayer()));
        }

        return List.copyOf(evidences);
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

    private String truncateContent(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > MAX_CONTENT_LENGTH
                ? content.substring(0, MAX_CONTENT_LENGTH) + "..."
                : content;
    }
}

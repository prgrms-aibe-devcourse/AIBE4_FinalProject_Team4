package kr.java.documind.domain.patchnote.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kr.java.documind.domain.archive.vector.model.repository.VectorStoreRepository;
import kr.java.documind.domain.patchnote.model.dto.ChunkDiffResult;
import kr.java.documind.domain.patchnote.model.dto.DocumentChunkWithMeta;
import kr.java.documind.global.enums.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 문서 버전 간 청크 단위 diff를 계산한다.
 *
 * <h3>처리 흐름</h3>
 * <ol>
 *   <li>동일 document_group에서 직전 버전 조회
 *   <li>현재/이전 버전의 벡터 스토어 청크 로드 (전체, 순서 보존)
 *   <li>Jaccard 유사도 기반 청크 정렬(align)
 *   <li>유사도 임계값으로 ADDED / MODIFIED / UNCHANGED / REMOVED 분류
 * </ol>
 *
 * <h3>청크 정렬 전략</h3>
 * <ul>
 *   <li>1차: 위치 기반 — 같은 chunk_index끼리 비교
 *   <li>2차: 최대 유사도 — 위치 기반이 임계값 미달이면 전체 후보 중 가장 유사한 청크 매칭
 * </ul>
 *
 * <p>이 서비스는 외부 LLM/임베딩 API를 사용하지 않으므로 빠르고 비용이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentDiffService {

    /** 동일 청크 판단 임계값 — 이 이상이면 UNCHANGED */
    static final double UNCHANGED_THRESHOLD = 0.85;

    /** 수정 청크 최저 임계값 — 이 이상이면 MODIFIED, 미만이면 ADDED로 간주 */
    static final double MODIFIED_THRESHOLD = 0.30;

    private final VectorStoreRepository vectorStoreRepository;
    private final kr.java.documind.domain.archive.document.model.repository.DocumentMetadataRepository
            documentMetadataRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 현재 문서와 직전 버전 간의 청크 diff 목록을 반환한다.
     *
     * <p>직전 버전이 없으면 전체 청크를 ADDED로 처리한다.
     *
     * @param currentDocumentId 현재 버전의 document_metadata.id
     * @param documentGroupId   document_group.id (같은 그룹 내 이전 버전 탐색용)
     * @return diff 결과 목록 (빈 목록 = 현재 버전 청크 없음)
     */
    public List<ChunkDiffResult> computeDiff(Long currentDocumentId, Long documentGroupId) {

        // 1. 현재 버전 청크 로드
        List<DocumentChunkWithMeta> currentChunks =
                loadChunks(currentDocumentId, SourceType.DOCUMENT);

        if (currentChunks.isEmpty()) {
            log.warn("[DocumentDiff] 현재 버전 청크 없음 — documentId: {}", currentDocumentId);
            return List.of();
        }

        // 2. 이전 버전 탐색
        List<kr.java.documind.domain.archive.document.model.entity.DocumentMetadata> prevVersions =
                documentMetadataRepository.findPreviousVersionsInGroup(
                        documentGroupId, currentDocumentId, PageRequest.of(0, 1));

        if (prevVersions.isEmpty()) {
            // 최초 버전 → 전체 청크 ADDED
            log.debug(
                    "[DocumentDiff] 이전 버전 없음 → 전체 ADDED ({} chunks) — documentId: {}",
                    currentChunks.size(),
                    currentDocumentId);
            return currentChunks.stream()
                    .map(
                            c ->
                                    new ChunkDiffResult(
                                            c.chunkIndex(), c.content(), null, "ADDED", 0.0))
                    .toList();
        }

        // 3. 이전 버전 청크 로드
        Long prevDocumentId = prevVersions.get(0).getId();
        List<DocumentChunkWithMeta> previousChunks =
                loadChunks(prevDocumentId, SourceType.DOCUMENT);

        log.debug(
                "[DocumentDiff] diff 계산 — current={} ({} chunks) ↔ prev={} ({} chunks)",
                currentDocumentId,
                currentChunks.size(),
                prevDocumentId,
                previousChunks.size());

        // 4. 정렬 + diff
        return alignAndDiff(currentChunks, previousChunks);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    private List<DocumentChunkWithMeta> loadChunks(Long documentId, SourceType sourceType) {
        List<String> contents =
                vectorStoreRepository.findAllContentsBySourceId(documentId, sourceType);
        return IntStream.range(0, contents.size())
                .mapToObj(i -> new DocumentChunkWithMeta(i, contents.get(i)))
                .toList();
    }

    /**
     * Jaccard 유사도 기반 청크 정렬 + diff 분류.
     *
     * <p>각 현재 청크에 대해 아직 매칭되지 않은 이전 청크 중 유사도가 가장 높은 것을 선택한다.
     * 매칭되지 않은 이전 청크는 REMOVED로 처리한다.
     */
    List<ChunkDiffResult> alignAndDiff(
            List<DocumentChunkWithMeta> current, List<DocumentChunkWithMeta> previous) {

        List<ChunkDiffResult> results = new ArrayList<>();
        Set<Integer> matchedPrevIndices = new HashSet<>();

        for (DocumentChunkWithMeta currentChunk : current) {

            double bestSimilarity = -1.0;
            DocumentChunkWithMeta bestMatch = null;
            int bestMatchIdx = -1;

            for (int j = 0; j < previous.size(); j++) {
                if (matchedPrevIndices.contains(j)) {
                    continue;
                }
                double sim = jaccardSimilarity(currentChunk.content(), previous.get(j).content());
                if (sim > bestSimilarity) {
                    bestSimilarity = sim;
                    bestMatch = previous.get(j);
                    bestMatchIdx = j;
                }
            }

            if (bestSimilarity >= UNCHANGED_THRESHOLD) {
                results.add(
                        new ChunkDiffResult(
                                currentChunk.chunkIndex(),
                                currentChunk.content(),
                                bestMatch.content(),
                                "UNCHANGED",
                                bestSimilarity));
                matchedPrevIndices.add(bestMatchIdx);

            } else if (bestSimilarity >= MODIFIED_THRESHOLD) {
                results.add(
                        new ChunkDiffResult(
                                currentChunk.chunkIndex(),
                                currentChunk.content(),
                                bestMatch.content(),
                                "MODIFIED",
                                bestSimilarity));
                matchedPrevIndices.add(bestMatchIdx);

            } else {
                // 매칭 이전 청크 없음 → ADDED
                results.add(
                        new ChunkDiffResult(
                                currentChunk.chunkIndex(),
                                currentChunk.content(),
                                null,
                                "ADDED",
                                0.0));
            }
        }

        // 매칭되지 않은 이전 청크 → REMOVED
        for (int j = 0; j < previous.size(); j++) {
            if (!matchedPrevIndices.contains(j)) {
                DocumentChunkWithMeta removed = previous.get(j);
                results.add(
                        new ChunkDiffResult(
                                removed.chunkIndex(), null, removed.content(), "REMOVED", 0.0));
            }
        }

        return results;
    }

    /**
     * 토큰 집합 기반 Jaccard 유사도.
     *
     * <p>|A ∩ B| / |A ∪ B| 로 계산한다.
     * 빈 문자열이거나 토큰이 없으면 0.0을 반환한다.
     */
    double jaccardSimilarity(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0.0;
        }
        Set<String> tokensA = tokenize(a);
        Set<String> tokensB = tokenize(b);
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0;
        }
        long intersection = tokensA.stream().filter(tokensB::contains).count();
        long union = (long) tokensA.size() + tokensB.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.split("[\\s.,;:!?()\r\n\\[\\]{}\"']+"))
                .filter(w -> w.length() > 1)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}

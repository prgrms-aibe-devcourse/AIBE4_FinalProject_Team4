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

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentDiffService {

    /** 동일 청크 판단 임계값 — 이 이상이면 UNCHANGED */
    static final double UNCHANGED_THRESHOLD = 0.85;

    /** 수정 청크 최저 임계값 — 이 이상이면 MODIFIED, 미만이면 ADDED로 간주 */
    static final double MODIFIED_THRESHOLD = 0.30;

    private final VectorStoreRepository vectorStoreRepository;
    private final kr.java.documind.domain.archive.document.model.repository
                    .DocumentMetadataRepository
            documentMetadataRepository;

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
                    .map(c -> new ChunkDiffResult(c.chunkIndex(), c.content(), null, "ADDED", 0.0))
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

    private List<DocumentChunkWithMeta> loadChunks(Long documentId, SourceType sourceType) {
        List<String> contents =
                vectorStoreRepository.findAllContentsBySourceId(documentId, sourceType);
        return IntStream.range(0, contents.size())
                .mapToObj(i -> new DocumentChunkWithMeta(i, contents.get(i)))
                .toList();
    }

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

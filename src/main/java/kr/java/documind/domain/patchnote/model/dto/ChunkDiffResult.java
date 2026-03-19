package kr.java.documind.domain.patchnote.model.dto;

/**
 * 두 버전 간 청크 단위 변경 분석 결과.
 *
 * <ul>
 *   <li>ADDED    — 현재 버전에만 존재 (previousContent = null)
 *   <li>MODIFIED — 양쪽에 존재하나 유사도가 임계값 미만
 *   <li>UNCHANGED — 유사도가 임계값 이상 (패치 후보에서 제외)
 *   <li>REMOVED  — 이전 버전에만 존재 (currentContent = null)
 * </ul>
 *
 * @param chunkIndex      현재 버전 기준 청크 순번 (REMOVED의 경우 이전 버전 순번)
 * @param currentContent  현재 버전 청크 텍스트 (REMOVED = null)
 * @param previousContent 이전 버전 청크 텍스트 (ADDED = null)
 * @param changeType      변경 유형: ADDED | MODIFIED | UNCHANGED | REMOVED
 * @param similarity      Jaccard 유사도 (ADDED/REMOVED = 0.0)
 */
public record ChunkDiffResult(
        int chunkIndex,
        String currentContent,
        String previousContent,
        String changeType,
        double similarity) {}

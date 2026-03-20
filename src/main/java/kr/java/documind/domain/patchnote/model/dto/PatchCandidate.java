package kr.java.documind.domain.patchnote.model.dto;

/**
 * 패치노트 포함 후보 — diff 청크에서 추출된 의미 있는 변경 항목.
 *
 * <p>점수 기반 필터링을 통과한 ADDED / MODIFIED 청크만 후보로 생성된다. {@code evidence}는 RAG 컨텍스트에 직접 삽입할 이전/현재 텍스트
 * 요약이다.
 *
 * @param chunkIndex 현재 버전 기준 청크 순번
 * @param currentContent 현재 버전 청크 텍스트 (ADDED = 신규 내용)
 * @param previousContent 이전 버전 청크 텍스트 (ADDED = null)
 * @param changeType ADDED | MODIFIED
 * @param score 패치노트 적합도 점수 (0.0 ~ 1.0)
 * @param evidence RAG 컨텍스트용 이전↔현재 텍스트 요약 (pending_item.evidence로 저장)
 */
public record PatchCandidate(
        int chunkIndex,
        String currentContent,
        String previousContent,
        String changeType,
        double score,
        String evidence) {}

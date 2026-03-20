package kr.java.documind.domain.patchnote.model.dto;

/**
 * 하이브리드 벡터 서치 결과 청크.
 *
 * <p>chunk_role 우선순위 (높을수록 먼저):
 *
 * <ul>
 *   <li>resolution / final_change / diff (5) — 해결·최종 변경 청크 (패치노트 핵심)
 *   <li>summary (3) — 요약 청크
 *   <li>background_resolution (2) — 배경+해결 병합 청크 (이슈)
 *   <li>background / comment (1) — 배경·댓글 청크
 *   <li>policy / faq / guide / troubleshooting / support (0) — 정책·가이드 청크 (패치노트 부적합)
 * </ul>
 *
 * @param sourceId 원본 소스 ID (pending_item.source_id)
 * @param sourceType 소스 타입 문자열 ("ISSUE" | "DOCUMENT")
 * @param content 청크 텍스트
 * @param chunkRole 청크 역할 (nullable)
 * @param hasNumericChange 수치 변경 여부
 * @param affectsPlayer 플레이어 직접 영향 여부
 * @param similarity 벡터 코사인 유사도 (0.0 ~ 1.0)
 * @param rrfScore RRF 복합 점수 (높을수록 우선)
 */
public record VectorChunkResult(
        Long sourceId,
        String sourceType,
        String content,
        String chunkRole,
        boolean hasNumericChange,
        boolean affectsPlayer,
        double similarity,
        double rrfScore) {

    /**
     * chunk_role 기반 우선순위 정수 반환.
     *
     * <p>{@link kr.java.documind.domain.patchnote.service.PatchNoteReranker}가 다중 신호 점수를 사용하므로, 이
     * 메서드는 단순 정렬이 필요한 경우에만 사용한다.
     */
    public int chunkRolePriority() {
        if (chunkRole == null) {
            return 0;
        }
        return switch (chunkRole) {
            case "resolution", "final_change", "diff" -> 5;
            case "summary" -> 3;
            case "background_resolution" -> 2;
            case "background", "comment" -> 1;
            default -> 0; // policy, faq, guide 등
        };
    }
}

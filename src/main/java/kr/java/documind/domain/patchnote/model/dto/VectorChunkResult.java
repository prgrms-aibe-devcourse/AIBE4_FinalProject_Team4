package kr.java.documind.domain.patchnote.model.dto;

/**
 * 하이브리드 벡터 서치 결과 청크.
 *
 * <p>chunk_role 우선순위 (높을수록 먼저):
 * <ul>
 *   <li>background_resolution (3) — 배경+해결 병합 청크 (이슈)
 *   <li>resolution (2) — 해결 정보 청크 (이슈)
 *   <li>background (1) — 배경 정보 청크 (이슈)
 *   <li>comment / null (0) — 댓글 청크 또는 문서 청크
 * </ul>
 *
 * @param sourceId         원본 소스 ID (pending_item.source_id)
 * @param sourceType       소스 타입 문자열 ("ISSUE" | "DOCUMENT")
 * @param content          청크 텍스트
 * @param chunkRole        청크 역할 (nullable, 이슈 전용)
 * @param hasNumericChange 수치 변경 여부 (reranking 1순위)
 * @param affectsPlayer    플레이어 직접 영향 여부 (reranking 2순위)
 * @param similarity       벡터 코사인 유사도 (0.0 ~ 1.0)
 * @param rrfScore         RRF 복합 점수 (높을수록 우선)
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
     * <p>재랭킹 비교에 사용한다 (내림차순 → 높을수록 앞).
     */
    public int chunkRolePriority() {
        if (chunkRole == null) {
            return 0;
        }
        return switch (chunkRole) {
            case "background_resolution" -> 3;
            case "resolution" -> 2;
            case "background" -> 1;
            default -> 0;
        };
    }
}

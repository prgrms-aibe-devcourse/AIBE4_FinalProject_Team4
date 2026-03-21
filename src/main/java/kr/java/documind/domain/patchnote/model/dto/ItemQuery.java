package kr.java.documind.domain.patchnote.model.dto;

import java.util.List;
import kr.java.documind.domain.patchnote.model.repository.HybridVectorSearchRepositoryCustom;
import kr.java.documind.domain.patchnote.util.ItemQueryBuilder;

/**
 * 단일 PendingItem에 대한 벡터/키워드 검색 쿼리 아티팩트.
 *
 * <p>{@link ItemQueryBuilder}가 PendingItem 하나에서 생성하며, {@link HybridVectorSearchRepositoryCustom}의
 * 항목별 검색 API에 전달된다.
 *
 * <p>이전의 모든 항목 요약을 하나로 합치던 방식과 달리, 항목 단위로 독립적인 쿼리를 보유하여 노이즈를 최소화하고 검색 정확도를 높인다.
 *
 * @param sourceId 이 항목의 source_id (vector_store 필터용)
 * @param itemRef 항목의 소스 REF (로깅·추적용, 예: "ISSUE-42", "DOC-17-0")
 * @param keyword 핵심 토큰을 공백으로 이어 붙인 문자열 — {@code similarity(content, :keyword)} 스코어링에 사용
 * @param tokens 개별 핵심 토큰 목록 — OR-based {@code LIKE} 조건 생성에 사용 (bigm GIN 인덱스 활용)
 * @param tsquery PostgreSQL {@code to_tsquery()} 식 (예: {@code "수정 | 추가 | 변경"}) — {@code ts_rank}
 *     스코어링에 사용. 유효 토큰이 없으면 null
 * @param embedding 이 항목 텍스트의 벡터 임베딩 — 임베딩 실패 시 null (키워드 전용 fallback)
 */
public record ItemQuery(
        Long sourceId,
        String itemRef,
        String keyword,
        List<String> tokens,
        String tsquery,
        float[] embedding) {

    /** 사용 가능한 임베딩 벡터가 있는지 여부. */
    public boolean hasEmbedding() {
        return embedding != null;
    }

    /** 사용 가능한 키워드(토큰)가 있는지 여부. */
    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank() && !tokens.isEmpty();
    }

    /** 사용 가능한 tsquery가 있는지 여부. */
    public boolean hasTsquery() {
        return tsquery != null && !tsquery.isBlank();
    }
}

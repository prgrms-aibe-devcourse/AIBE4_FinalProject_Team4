package kr.java.documind.domain.patchnote.model.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kr.java.documind.domain.patchnote.model.dto.ItemQuery;
import kr.java.documind.domain.patchnote.model.dto.VectorChunkResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 패치노트 초안 생성을 위한 하이브리드 벡터 서치 레포지토리.
 *
 * <h3>항목별 검색 API (권장)</h3>
 *
 * {@link #searchForItem(String, ItemQuery, int)} — PendingItem 하나의 {@link ItemQuery}를 받아 해당
 * source_id로 스코프를 제한한 하이브리드 서치를 수행한다. 항목 단위로 독립적인 쿼리를 사용하므로, 여러 항목의 요약을 합쳐 쿼리하던 이전 방식에 비해 노이즈가 적다.
 *
 * <h3>검색 전략 선택 (자동)</h3>
 *
 * <ul>
 *   <li>임베딩 + 키워드 → RRF 하이브리드 (벡터 + OR-LIKE)
 *   <li>임베딩만 → 벡터 유사도 전용
 *   <li>키워드만 → OR-LIKE + pg_bigm similarity 정렬 (미설치 시 id 순 폴백)
 *   <li>둘 다 없음 → source_id 직접 조회 (chunk_index 오름차순)
 * </ul>
 *
 * <h3>RRF 공식</h3>
 *
 * <pre>rrf_score = 1/(k + vec_rank) + 1/(k + kw_rank)   (k = 60)</pre>
 *
 * <h3>pg_bigm similarity() 폴백</h3>
 *
 * pg_bigm 확장이 설치되지 않은 환경(로컬 PostgreSQL 등)에서는 {@code similarity()} 함수가 존재하지 않는다. 첫 호출 시 런타임 감지를
 * 수행하며, 미설치 시 키워드 정렬을 {@code id ASC}로 대체한다. Docker 환경(Dockerfile.postgres)에서는 항상 pg_bigm이 설치된다.
 *
 * <h3>SQL 인젝션 방어</h3>
 *
 * <ul>
 *   <li>pgvector 리터럴: 내부 생성 float[] → 직접 SQL 포맷 (사용자 입력 아님, 안전)
 *   <li>source_id: Long 리터럴로 직접 포맷 (사용자 입력 아님, 안전)
 *   <li>projectId, keyword, 토큰: {@link NamedParameterJdbcTemplate} named param으로 바인딩
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class HybridVectorSearchRepositoryCustom {

    /** RRF 스코어 계산 상수 (표준값 60). */
    private static final int RRF_K = 60;

    /** 각 서브쿼리(벡터/키워드)별 후보 수 배수. */
    private static final int PER_SEARCH_MULTIPLIER = 3;

    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;

    /**
     * pg_bigm {@code similarity()} 함수 가용 여부. {@code null} = 미감지, {@code true/false} = 감지 완료 (한 번만
     * 실행).
     */
    private volatile Boolean hasBigmSimilarityFunction = null;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — 항목별 검색 (권장)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 단일 {@link ItemQuery}에 대한 하이브리드 서치를 수행한다.
     *
     * <p>검색 범위는 {@code ItemQuery.sourceId}에 해당하는 벡터 청크로 제한된다. 임베딩/키워드 가용 여부에 따라 검색 전략이 자동 선택된다.
     *
     * @param projectId 프로젝트 UUID 문자열 (metadata 필터)
     * @param query 항목별 쿼리 아티팩트
     * @param limit 최대 반환 청크 수
     * @return RRF 스코어 내림차순 정렬된 청크 목록
     */
    public List<VectorChunkResult> searchForItem(String projectId, ItemQuery query, int limit) {
        boolean hasVector = query.hasEmbedding();
        boolean hasKeyword = query.hasKeyword();

        log.debug(
                "searchForItem — ref={}, sourceId={}, hasVector={}, hasKeyword={}, limit={}",
                query.itemRef(),
                query.sourceId(),
                hasVector,
                hasKeyword,
                limit);

        if (hasVector && hasKeyword) {
            return searchItemHybrid(projectId, query, limit);
        } else if (hasVector) {
            return searchItemVector(projectId, query, limit);
        } else if (hasKeyword) {
            return searchItemKeyword(projectId, query, limit);
        } else {
            return fetchItemBySourceId(projectId, query.sourceId(), limit);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 항목별 검색 전략 구현
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * RRF 하이브리드 검색 (벡터 + OR-LIKE 키워드).
     *
     * <p>vec_ranked: 코사인 유사도 기준 벡터 검색
     *
     * <p>kw_ranked: OR-LIKE 조건 + pg_bigm {@code similarity(content, :keyword)} 정렬 (pg_bigm 미설치 시
     * {@code id ASC} 폴백)
     *
     * <p>두 결과를 FULL OUTER JOIN 후 RRF 점수 합산.
     */
    private List<VectorChunkResult> searchItemHybrid(String projectId, ItemQuery query, int limit) {

        int perLimit = limit * PER_SEARCH_MULTIPLIER;
        String vectorLiteral = toVectorLiteral(query.embedding());
        String orLikeClause = buildOrLikeClause(query.tokens());
        String kwSimExpr = kwSimExpr();
        String kwOrderExpr = kwOrderExpr();

        String sql =
                """
                WITH vec_ranked AS (
                    SELECT id, source_id, content, metadata,
                           1 - (embedding <=> '%s'::vector) AS similarity,
                           ROW_NUMBER() OVER (ORDER BY embedding <=> '%s'::vector) AS rnk
                    FROM vector_store
                    WHERE metadata->>'project_id' = :projectId
                      AND source_id = %d
                    ORDER BY embedding <=> '%s'::vector
                    LIMIT %d
                ),
                kw_ranked AS (
                    SELECT id, source_id, content, metadata,
                           %s AS similarity,
                           ROW_NUMBER() OVER (ORDER BY %s) AS rnk
                    FROM vector_store
                    WHERE metadata->>'project_id' = :projectId
                      AND source_id = %d
                      AND (%s)
                    LIMIT %d
                ),
                combined AS (
                    SELECT
                        COALESCE(v.source_id, k.source_id)   AS source_id,
                        COALESCE(v.content,   k.content)     AS content,
                        COALESCE(v.metadata,  k.metadata)    AS metadata,
                        COALESCE(v.similarity, 0.0)          AS similarity,
                        COALESCE(1.0 / (%d + v.rnk::float), 0.0)
                            + COALESCE(1.0 / (%d + k.rnk::float), 0.0) AS rrf_score
                    FROM vec_ranked v
                    FULL OUTER JOIN kw_ranked k ON v.id = k.id
                )
                SELECT source_id, content, metadata, similarity, rrf_score
                FROM combined
                ORDER BY rrf_score DESC
                LIMIT :limit
                """
                        .formatted(
                                vectorLiteral,
                                vectorLiteral,
                                query.sourceId(),
                                vectorLiteral,
                                perLimit,
                                kwSimExpr,
                                kwOrderExpr,
                                query.sourceId(),
                                orLikeClause,
                                perLimit,
                                RRF_K,
                                RRF_K);

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("keyword", query.keyword())
                        .addValue("limit", limit);
        addTokenParams(params, query.tokens());

        return namedJdbc.query(sql, params, (rs, rowNum) -> mapRow(rs));
    }

    /** 벡터 유사도 전용 검색. */
    private List<VectorChunkResult> searchItemVector(String projectId, ItemQuery query, int limit) {

        String vectorLiteral = toVectorLiteral(query.embedding());

        String sql =
                """
                SELECT source_id, content, metadata,
                       1 - (embedding <=> '%s'::vector) AS similarity,
                       1.0 / (%d + ROW_NUMBER() OVER (
                           ORDER BY embedding <=> '%s'::vector
                       )::float) AS rrf_score
                FROM vector_store
                WHERE metadata->>'project_id' = :projectId
                  AND source_id = %d
                ORDER BY embedding <=> '%s'::vector
                LIMIT :limit
                """
                        .formatted(
                                vectorLiteral,
                                RRF_K,
                                vectorLiteral,
                                query.sourceId(),
                                vectorLiteral);

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("limit", limit);

        return namedJdbc.query(sql, params, (rs, rowNum) -> mapRow(rs));
    }

    /**
     * OR-LIKE 키워드 전용 검색.
     *
     * <p>pg_bigm {@code similarity(content, :keyword)} 함수로 관련성 높은 청크를 우선 반환한다. OR-LIKE 조건으로 GIN
     * 인덱스를 활용하여 후보를 빠르게 필터링한 뒤 유사도 정렬을 적용한다. pg_bigm 미설치 환경에서는 {@code id ASC} 정렬로 폴백한다.
     */
    private List<VectorChunkResult> searchItemKeyword(
            String projectId, ItemQuery query, int limit) {

        String orLikeClause = buildOrLikeClause(query.tokens());
        String kwSimExpr = kwSimExpr();
        String kwOrderExpr = kwOrderExpr();

        String sql =
                """
                SELECT source_id, content, metadata,
                       %s AS similarity,
                       1.0 / (%d + ROW_NUMBER() OVER (ORDER BY %s)::float) AS rrf_score
                FROM vector_store
                WHERE metadata->>'project_id' = :projectId
                  AND source_id = %d
                  AND (%s)
                ORDER BY %s
                LIMIT :limit
                """
                        .formatted(
                                kwSimExpr,
                                RRF_K,
                                kwOrderExpr,
                                query.sourceId(),
                                orLikeClause,
                                kwOrderExpr);

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("keyword", query.keyword())
                        .addValue("limit", limit);
        addTokenParams(params, query.tokens());

        return namedJdbc.query(sql, params, (rs, rowNum) -> mapRow(rs));
    }

    /** source_id 기반 직접 조회 (쿼리 없이 전체 청크 반환). */
    private List<VectorChunkResult> fetchItemBySourceId(
            String projectId, Long sourceId, int limit) {

        String sql =
                """
                SELECT source_id, content, metadata,
                       0.0 AS similarity,
                       0.0 AS rrf_score
                FROM vector_store
                WHERE metadata->>'project_id' = :projectId
                  AND source_id = %d
                ORDER BY COALESCE((metadata->>'chunk_index')::int, 0)
                LIMIT :limit
                """
                        .formatted(sourceId);

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("limit", limit);

        return namedJdbc.query(sql, params, (rs, rowNum) -> mapRow(rs));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SQL 빌더 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * pg_bigm {@code similarity()} 함수 가용 여부를 런타임에 감지한다.
     *
     * <p>최초 호출 시에만 테스트 쿼리를 실행하고 결과를 캐시한다. Docker(Dockerfile.postgres) 환경에서는 pg_bigm이 항상 설치되어 있으므로
     * {@code true}를 반환한다. 로컬 PostgreSQL 등 pg_bigm 미설치 환경에서는 {@code false}를 반환하고 폴백 SQL을 사용한다.
     */
    private boolean hasBigmSimilarityFunction() {
        if (hasBigmSimilarityFunction != null) {
            return hasBigmSimilarityFunction;
        }
        try {
            namedJdbc
                    .getJdbcTemplate()
                    .queryForObject(
                            "SELECT bigm_similarity('hello'::text, 'world'::text)", Double.class);
            hasBigmSimilarityFunction = true;
            log.info("pg_bigm bigm_similarity() 감지 성공 — 키워드 유사도 정렬 활성화");
        } catch (Exception e) {
            hasBigmSimilarityFunction = false;
            log.warn("pg_bigm bigm_similarity() 미지원 — 키워드 정렬을 id ASC로 대체합니다.");
        }
        return hasBigmSimilarityFunction;
    }

    private String kwSimExpr() {
        return hasBigmSimilarityFunction()
                ? "bigm_similarity(content, CAST(:keyword AS text))"
                : "1.0";
    }

    private String kwOrderExpr() {
        return hasBigmSimilarityFunction()
                ? "bigm_similarity(content, CAST(:keyword AS text)) DESC"
                : "id ASC";
    }

    /**
     * 토큰 목록으로부터 OR-LIKE 절을 생성한다.
     *
     * <p>예) tokens=["결제","버튼"] → {@code content LIKE :tok0 OR content LIKE :tok1}
     *
     * <p>named param에는 {@link #addTokenParams}로 {@code :tok0 = "%결제%"} 형태로 바인딩한다. pg_bigm GIN 인덱스가
     * LIKE '%keyword%' 패턴을 가속화한다.
     */
    private String buildOrLikeClause(List<String> tokens) {
        if (tokens.isEmpty()) {
            return "TRUE";
        }
        return IntStream.range(0, tokens.size())
                .mapToObj(i -> "content LIKE :tok" + i)
                .collect(Collectors.joining(" OR "));
    }

    /**
     * OR-LIKE에 사용되는 토큰 파라미터를 {@link MapSqlParameterSource}에 추가한다.
     *
     * <p>{@code :tok0}, {@code :tok1}, ... 에 {@code "%token%"} 값을 바인딩한다.
     */
    private void addTokenParams(MapSqlParameterSource params, List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String escaped =
                    tokens.get(i).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            params.addValue("tok" + i, "%" + escaped + "%");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ResultSet → DTO 매핑
    // ─────────────────────────────────────────────────────────────────────────

    private VectorChunkResult mapRow(ResultSet rs) throws SQLException {
        long sourceId = rs.getLong("source_id");
        String content = rs.getString("content");
        double similarity = rs.getDouble("similarity");
        double rrfScore = rs.getDouble("rrf_score");

        JsonNode metadata = parseMetadata(rs.getString("metadata"));

        String chunkRole = getMetadataString(metadata, "chunk_role");
        String sourceType = getMetadataString(metadata, "source_type");
        boolean hasNumericChange = getMetadataBoolean(metadata, "has_numeric_change");
        boolean affectsPlayer = getMetadataBoolean(metadata, "affects_player");

        return new VectorChunkResult(
                sourceId,
                sourceType,
                content,
                chunkRole,
                hasNumericChange,
                affectsPlayer,
                similarity,
                rrfScore);
    }

    private JsonNode parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("벡터 메타데이터 JSON 파싱 실패: {}", json, e);
            return objectMapper.createObjectNode();
        }
    }

    private String getMetadataString(JsonNode metadata, String field) {
        JsonNode node = metadata.get(field);
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private boolean getMetadataBoolean(JsonNode metadata, String field) {
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return "true".equalsIgnoreCase(node.asText());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 벡터 직렬화
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * float[] 임베딩을 PostgreSQL pgvector 리터럴 문자열로 변환한다.
     *
     * <p>내부에서만 생성되는 float[] 이므로 SQL 인젝션 위험이 없다.
     */
    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 10);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}

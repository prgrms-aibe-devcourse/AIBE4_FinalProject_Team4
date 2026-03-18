package kr.java.documind.domain.patchnote.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import kr.java.documind.domain.patchnote.model.dto.VectorChunkResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 패치노트 초안 생성을 위한 하이브리드 벡터 서치 레포지토리.
 *
 * <p>pgvector 코사인 유사도 검색 + pg_bigm 키워드 검색을 결합하여
 * RRF(Reciprocal Rank Fusion) 스코어로 순위를 결정한다.
 *
 * <pre>
 * RRF 공식: rrf_score = 1/(k + vec_rank) + 1/(k + kw_rank)  (k = 60)
 * </pre>
 *
 * <p>queryVector와 keyword 중 하나만 제공된 경우 해당 방식으로만 검색하고,
 * 둘 다 null이면 project_id + source_id 기반 단순 조회를 수행한다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class HybridVectorSearchRepository {

    /** RRF 스코어 계산 상수 (표준값 60). */
    private static final int RRF_K = 60;

    /** 각 서브쿼리(벡터/키워드)별 후보 수 배수. */
    private static final int PER_SEARCH_MULTIPLIER = 3;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 하이브리드 서치 수행.
     *
     * <p>제공된 파라미터에 따라 자동으로 방식을 선택한다:
     * <ul>
     *   <li>queryVector + keyword → RRF 하이브리드
     *   <li>queryVector만 → 벡터 유사도 검색
     *   <li>keyword만 → pg_bigm 키워드 검색
     *   <li>둘 다 null → source_id 직접 조회 (chunk_index 오름차순)
     * </ul>
     *
     * @param projectId   프로젝트 UUID 문자열 (metadata 필터)
     * @param sourceIds   조회 대상 source_id 목록 (null이면 프로젝트 전체)
     * @param keyword     pg_bigm 키워드 (null 허용)
     * @param queryVector 벡터 쿼리 임베딩 (null 허용)
     * @param limit       최대 반환 청크 수
     * @return RRF 스코어 내림차순 정렬된 청크 목록
     */
    public List<VectorChunkResult> hybridSearch(
            String projectId,
            List<Long> sourceIds,
            String keyword,
            float[] queryVector,
            int limit) {

        if (sourceIds != null && sourceIds.isEmpty()) {
            return Collections.emptyList();
        }

        boolean hasVector = queryVector != null;
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasVector && hasKeyword) {
            return searchHybridRrf(projectId, sourceIds, keyword, queryVector, limit);
        } else if (hasVector) {
            return searchVector(projectId, sourceIds, queryVector, limit);
        } else if (hasKeyword) {
            return searchKeyword(projectId, sourceIds, keyword, limit);
        } else {
            return fetchBySourceIds(projectId, sourceIds, limit);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 검색 전략별 구현
    // ─────────────────────────────────────────────────────────────────────────

    /** RRF 하이브리드 검색 (벡터 + 키워드). */
    private List<VectorChunkResult> searchHybridRrf(
            String projectId,
            List<Long> sourceIds,
            String keyword,
            float[] queryVector,
            int limit) {

        int perSearchLimit = limit * PER_SEARCH_MULTIPLIER;
        String sourceFilter = buildSourceFilter(sourceIds);
        String vectorStr = toVectorString(queryVector);
        String likeKeyword = "%" + keyword.replace("%", "\\%").replace("_", "\\_") + "%";

        String sql =
                """
                WITH vec_ranked AS (
                    SELECT id, source_id, content, metadata,
                           1 - (embedding <=> '%s'::vector) AS similarity,
                           ROW_NUMBER() OVER (ORDER BY embedding <=> '%s'::vector) AS rnk
                    FROM vector_store
                    WHERE metadata->>'project_id' = ?
                      %s
                    ORDER BY embedding <=> '%s'::vector
                    LIMIT %d
                ),
                kw_ranked AS (
                    SELECT id, source_id, content, metadata,
                           0.0 AS similarity,
                           ROW_NUMBER() OVER (ORDER BY id) AS rnk
                    FROM vector_store
                    WHERE metadata->>'project_id' = ?
                      %s
                      AND content LIKE ?
                    LIMIT %d
                ),
                combined AS (
                    SELECT
                        COALESCE(v.source_id, k.source_id) AS source_id,
                        COALESCE(v.content, k.content)     AS content,
                        COALESCE(v.metadata, k.metadata)   AS metadata,
                        COALESCE(v.similarity, 0.0)        AS similarity,
                        COALESCE(1.0 / (%d + v.rnk::float), 0.0)
                            + COALESCE(1.0 / (%d + k.rnk::float), 0.0) AS rrf_score
                    FROM vec_ranked v
                    FULL OUTER JOIN kw_ranked k ON v.id = k.id
                )
                SELECT source_id, content, metadata, similarity, rrf_score
                FROM combined
                ORDER BY rrf_score DESC
                LIMIT ?
                """
                        .formatted(
                                vectorStr, vectorStr, sourceFilter,
                                vectorStr, perSearchLimit,
                                sourceFilter, perSearchLimit,
                                RRF_K, RRF_K);

        log.debug("RRF 하이브리드 서치 — projectId={}, sourceIds={}, keyword={}", projectId, sourceIds, keyword);

        List<Object> params = new ArrayList<>();
        params.add(projectId);
        params.addAll(buildSourceParams(sourceIds));
        params.add(projectId);
        params.addAll(buildSourceParams(sourceIds));
        params.add(likeKeyword);
        params.add(limit);

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs), params.toArray());
    }

    /** 벡터 유사도 전용 검색. */
    private List<VectorChunkResult> searchVector(
            String projectId,
            List<Long> sourceIds,
            float[] queryVector,
            int limit) {

        String sourceFilter = buildSourceFilter(sourceIds);
        String vectorStr = toVectorString(queryVector);

        String sql =
                """
                SELECT source_id, content, metadata,
                       1 - (embedding <=> '%s'::vector) AS similarity,
                       1.0 / (%d + ROW_NUMBER() OVER (ORDER BY embedding <=> '%s'::vector)::float) AS rrf_score
                FROM vector_store
                WHERE metadata->>'project_id' = ?
                  %s
                ORDER BY embedding <=> '%s'::vector
                LIMIT ?
                """
                        .formatted(vectorStr, RRF_K, vectorStr, sourceFilter, vectorStr);

        List<Object> params = new ArrayList<>();
        params.add(projectId);
        params.addAll(buildSourceParams(sourceIds));
        params.add(limit);

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs), params.toArray());
    }

    /** pg_bigm 키워드 전용 검색. */
    private List<VectorChunkResult> searchKeyword(
            String projectId,
            List<Long> sourceIds,
            String keyword,
            int limit) {

        String sourceFilter = buildSourceFilter(sourceIds);
        String likeKeyword = "%" + keyword.replace("%", "\\%").replace("_", "\\_") + "%";

        String sql =
                """
                SELECT source_id, content, metadata,
                       0.0 AS similarity,
                       1.0 / (%d + ROW_NUMBER() OVER (ORDER BY id)::float) AS rrf_score
                FROM vector_store
                WHERE metadata->>'project_id' = ?
                  %s
                  AND content LIKE ?
                LIMIT ?
                """
                        .formatted(RRF_K, sourceFilter);

        List<Object> params = new ArrayList<>();
        params.add(projectId);
        params.addAll(buildSourceParams(sourceIds));
        params.add(likeKeyword);
        params.add(limit);

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs), params.toArray());
    }

    /** source_id 기반 직접 조회 (쿼리 없이 전체 청크 반환). */
    private List<VectorChunkResult> fetchBySourceIds(
            String projectId, List<Long> sourceIds, int limit) {

        String sourceFilter = buildSourceFilter(sourceIds);

        String sql =
                """
                SELECT source_id, content, metadata,
                       0.0 AS similarity,
                       0.0 AS rrf_score
                FROM vector_store
                WHERE metadata->>'project_id' = ?
                  %s
                ORDER BY source_id, COALESCE((metadata->>'chunk_index')::int, 0)
                LIMIT ?
                """
                        .formatted(sourceFilter);

        List<Object> params = new ArrayList<>();
        params.add(projectId);
        params.addAll(buildSourceParams(sourceIds));
        params.add(limit);

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs), params.toArray());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SQL 빌더 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * source_id 필터 SQL 조각 생성.
     *
     * <p>sourceIds가 null이면 빈 문자열(프로젝트 전체 대상).
     * Long 리터럴로 구성하므로 SQL Injection 위험 없음.
     */
    private String buildSourceFilter(List<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return "";
        }
        String ids = sourceIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return "AND source_id IN (" + ids + ")";
    }

    /** source_id 필터에 대응하는 바인딩 파라미터 목록 (Direct Literal 사용 시 빈 리스트). */
    private List<Object> buildSourceParams(List<Long> sourceIds) {
        // buildSourceFilter에서 리터럴로 처리하므로 파라미터 없음
        return Collections.emptyList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ResultSet → DTO 매핑
    // ─────────────────────────────────────────────────────────────────────────

    private VectorChunkResult mapRow(ResultSet rs) throws SQLException {
        long sourceId = rs.getLong("source_id");
        String content = rs.getString("content");
        double similarity = rs.getDouble("similarity");
        double rrfScore = rs.getDouble("rrf_score");

        String metadataJson = rs.getString("metadata");
        JsonNode metadata = parseMetadata(metadataJson);

        String chunkRole = getMetadataString(metadata, "chunk_role");
        String sourceType = getMetadataString(metadata, "source_type");
        boolean hasNumericChange = getMetadataBoolean(metadata, "has_numeric_change");
        boolean affectsPlayer = getMetadataBoolean(metadata, "affects_player");

        return new VectorChunkResult(
                sourceId, sourceType, content, chunkRole,
                hasNumericChange, affectsPlayer, similarity, rrfScore);
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
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private boolean getMetadataBoolean(JsonNode metadata, String field) {
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull()) {
            return false;
        }
        // JSONB에서 "true"/"false" 문자열 또는 boolean으로 저장될 수 있음
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return "true".equalsIgnoreCase(node.asText());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 벡터 직렬화
    // ─────────────────────────────────────────────────────────────────────────

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 10);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}

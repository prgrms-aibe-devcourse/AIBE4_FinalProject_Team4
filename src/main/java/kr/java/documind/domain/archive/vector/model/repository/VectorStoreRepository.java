package kr.java.documind.domain.archive.vector.model.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.exception.StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class VectorStoreRepository {

    private static final String INSERT_CHUNK_SQL =
            "INSERT INTO vector_store (source_id, content, metadata, embedding)"
                    + " VALUES (?, ?, ?::jsonb, ?::vector)";

    private static final String DELETE_BY_SOURCE_ID_SQL =
            "DELETE FROM vector_store WHERE source_id = ? AND metadata->>'source_type' = ?";

    private static final String FIND_CONTENTS_BY_SOURCE_ID_SQL =
            "SELECT content FROM vector_store WHERE source_id = ? AND metadata->>'source_type' = ?"
                    + " ORDER BY COALESCE((metadata->>'chunk_index')::int, 0) LIMIT ?";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void insertChunks(Long sourceId, List<Document> chunks, List<float[]> embeddings) {
        if (chunks.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                INSERT_CHUNK_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Document chunk = chunks.get(i);
                        ps.setLong(1, sourceId);
                        ps.setString(2, chunk.getText() != null ? chunk.getText() : "");
                        ps.setString(3, toJsonb(chunk.getMetadata()));
                        ps.setString(4, toVectorString(embeddings.get(i)));
                    }

                    @Override
                    public int getBatchSize() {
                        return chunks.size();
                    }
                });
    }

    public void deleteBySourceId(Long sourceId, SourceType sourceType) {
        jdbcTemplate.update(DELETE_BY_SOURCE_ID_SQL, sourceId, sourceType.name());
    }

    public List<String> findContentsBySourceId(Long sourceId, SourceType sourceType, int limit) {
        return jdbcTemplate.queryForList(
                FIND_CONTENTS_BY_SOURCE_ID_SQL, String.class, sourceId, sourceType.name(), limit);
    }

    private String toJsonb(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new StorageException("벡터 메타데이터 직렬화에 실패했습니다.", e);
        }
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 10);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding[i]);
        }
        return sb.append("]").toString();
    }
}

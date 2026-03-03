package kr.java.documind.domain.logprocessor.model.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class LogJdbcRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${worker.jdbc.batch-size}")
    private int batchSize;

    @PostConstruct
    public void init() {
        if (batchSize <= 0) {
            batchSize = 1000;
        }
    }

    @Transactional
    public void saveAll(List<GameLog> logs) {
        String sql =
                "INSERT INTO game_log (log_id, project_id, session_id, user_id, severity,"
                        + " event_category, body, occurred_at, ingested_at, trace_id, span_id,"
                        + " fingerprint, resource, attributes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)";

        int totalSize = logs.size();
        for (int i = 0; i < totalSize; i += batchSize) {
            List<GameLog> batchList = logs.subList(i, Math.min(totalSize, i + batchSize));

            jdbcTemplate.batchUpdate(
                    sql,
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int j) throws SQLException {
                            GameLog log = batchList.get(j);
                            ps.setObject(1, log.getLogId());
                            ps.setString(2, log.getProjectId());
                            ps.setString(3, log.getSessionId());
                            ps.setString(4, log.getUserId());
                            ps.setString(5, log.getSeverity().toString());
                            ps.setString(6, log.getEventCategory().toString());
                            ps.setString(7, log.getBody());
                            ps.setObject(8, log.getOccurredAt());
                            ps.setObject(9, log.getIngestedAt());
                            ps.setString(10, log.getTraceId());
                            ps.setString(11, log.getSpanId());
                            ps.setString(12, log.getFingerprint());

                            try {
                                ps.setString(
                                        13, objectMapper.writeValueAsString(log.getResource()));
                                ps.setString(
                                        14, objectMapper.writeValueAsString(log.getAttributes()));
                            } catch (JsonProcessingException e) {
                                throw new SQLException("Error converting map to json", e);
                            }
                        }

                        @Override
                        public int getBatchSize() {
                            return batchList.size();
                        }
                    });
        }
    }
}

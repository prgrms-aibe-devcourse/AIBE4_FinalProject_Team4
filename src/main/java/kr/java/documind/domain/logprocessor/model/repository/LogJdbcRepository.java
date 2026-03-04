package kr.java.documind.domain.logprocessor.model.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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

    private static final int MIN_BATCH_SIZE = 10; // 최소 배치 크기

    @Transactional
    public void saveAll(List<GameLog> logs) {
        saveAllWithRetry(logs, 0, logs.size(), batchSize);
    }

    /**
     * Deadlock 발생 시 배치를 절반으로 나누어 재시도하는 로직 (범위 기반)
     *
     * @param logs 저장할 로그 리스트
     * @param startIndex 시작 인덱스 (inclusive)
     * @param endIndex 종료 인덱스 (exclusive)
     * @param currentBatchSize 현재 배치 크기
     */
    private void saveAllWithRetry(
            List<GameLog> logs, int startIndex, int endIndex, int currentBatchSize) {
        try {
            saveBatch(logs, startIndex, endIndex, currentBatchSize);
        } catch (PessimisticLockingFailureException e) {
            // Deadlock 및 Lock 획득 실패 처리
            handleDeadlock(logs, startIndex, endIndex, currentBatchSize, e);
        }
    }

    /**
     * Deadlock 예외 처리: 배치를 절반으로 나누어 재시도
     *
     * @param logs 저장할 로그 리스트
     * @param startIndex 시작 인덱스
     * @param endIndex 종료 인덱스
     * @param currentBatchSize 현재 배치 크기
     * @param e 발생한 Deadlock 예외
     */
    private void handleDeadlock(
            List<GameLog> logs,
            int startIndex,
            int endIndex,
            int currentBatchSize,
            RuntimeException e) {
        int newBatchSize = currentBatchSize / 2;

        if (newBatchSize < MIN_BATCH_SIZE) {
            log.error(
                    "[Deadlock Retry] Batch size reached minimum ({}). Giving up on {} logs"
                            + " (range: {}-{}).",
                    MIN_BATCH_SIZE,
                    endIndex - startIndex,
                    startIndex,
                    endIndex);
            throw e; // DLQ로 전달
        }

        log.warn(
                "[Deadlock Retry] Deadlock detected with batch size {}. Retrying range {}-{} with"
                        + " batch size {}",
                currentBatchSize,
                startIndex,
                endIndex,
                newBatchSize);

        // 같은 범위를 더 작은 배치 크기로 재귀 재시도
        saveAllWithRetry(logs, startIndex, endIndex, newBatchSize);
    }

    /**
     * 실제 배치 삽입 수행 (범위 기반)
     *
     * @param logs 저장할 로그 리스트
     * @param startIndex 시작 인덱스 (inclusive)
     * @param endIndex 종료 인덱스 (exclusive)
     * @param currentBatchSize 현재 배치 크기
     */
    private void saveBatch(List<GameLog> logs, int startIndex, int endIndex, int currentBatchSize) {
        String sql =
                "INSERT INTO game_log (log_id, project_id, session_id, user_id, severity,"
                        + " event_category, body, occurred_at, ingested_at, trace_id, span_id,"
                        + " fingerprint, resource, attributes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)";

        int rangeSize = endIndex - startIndex;
        for (int i = startIndex; i < endIndex; i += currentBatchSize) {
            int batchEnd = Math.min(endIndex, i + currentBatchSize);
            List<GameLog> batchList = logs.subList(i, batchEnd);

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

            log.debug(
                    "Successfully saved batch {}/{} (range: {}-{}, size: {})",
                    ((i - startIndex) / currentBatchSize) + 1,
                    (rangeSize + currentBatchSize - 1) / currentBatchSize,
                    i,
                    batchEnd,
                    batchList.size());
        }
    }
}

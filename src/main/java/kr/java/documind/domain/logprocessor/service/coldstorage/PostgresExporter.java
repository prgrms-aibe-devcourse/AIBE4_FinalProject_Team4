package kr.java.documind.domain.logprocessor.service.coldstorage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL 데이터 Export 유틸리티
 *
 * <p>PostgreSQL COPY 명령을 사용하여 테이블 데이터를 CSV로 export
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostgresExporter {

    private final DataSource dataSource;

    /**
     * 파티션 테이블명 검증 패턴 (SQL 인젝션 방지)
     *
     * <p>허용 형식: game_log_YYYY_wWW (예: game_log_2024_w10)
     */
    private static final Pattern PARTITION_NAME_PATTERN =
            Pattern.compile("^game_log_\\d{4}_w\\d{2}$");

    /**
     * PostgreSQL 테이블을 CSV 파일로 export
     *
     * <p><b>보안:</b> SQL 인젝션 방지를 위한 테이블명 검증
     *
     * @param tableName 테이블 이름
     * @param outputFile 출력 CSV 파일
     * @throws IOException 파일 I/O 오류
     * @throws IllegalArgumentException 잘못된 테이블명
     */
    public void exportTableToCsv(String tableName, File outputFile) throws IOException {
        log.info("[PostgresExporter] Exporting table {} to {}", tableName, outputFile.getName());

        // SQL 인젝션 방지: 테이블명 검증
        validatePartitionTableName(tableName);

        try (Connection connection = dataSource.getConnection();
                FileWriter writer = new FileWriter(outputFile)) {

            // PostgreSQL COPY 명령 사용 (Hikari 프록시 언래핑)
            BaseConnection baseConnection = connection.unwrap(BaseConnection.class);
            CopyManager copyManager = new CopyManager(baseConnection);

            // COPY 쿼리: CSV 형식으로 export
            String copyQuery =
                    String.format(
                            """
                    COPY (
                        SELECT
                            log_id,
                            project_id,
                            session_id,
                            user_id,
                            severity,
                            event_category,
                            archive,
                            occurred_at,
                            ingested_at,
                            trace_id,
                            span_id,
                            fingerprint,
                            resource::text as resource,
                            attributes::text as attributes,
                            created_at,
                            updated_at
                        FROM %s
                        ORDER BY occurred_at
                    ) TO STDOUT WITH (FORMAT CSV, HEADER true, DELIMITER ',', QUOTE '\"')
                    """,
                            tableName);

            // COPY 실행
            long rowCount = copyManager.copyOut(copyQuery, writer);

            log.info("[PostgresExporter] Exported {} rows from {}", rowCount, tableName);

        } catch (Exception e) {
            log.error("[PostgresExporter] Failed to export table {}", tableName, e);
            throw new IOException("PostgreSQL export failed: " + tableName, e);
        }
    }

    /**
     * 테이블 행 개수 조회
     *
     * <p><b>보안:</b> SQL 인젝션 방지를 위한 테이블명 검증
     *
     * @param tableName 테이블 이름
     * @return 행 개수
     * @throws IllegalArgumentException 잘못된 테이블명
     */
    public long getTableRowCount(String tableName) {
        // SQL 인젝션 방지: 테이블명 검증
        validatePartitionTableName(tableName);

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            String query = String.format("SELECT COUNT(*) FROM %s", tableName);
            ResultSet rs = statement.executeQuery(query);

            if (rs.next()) {
                return rs.getLong(1);
            }

            return 0;

        } catch (Exception e) {
            log.error("[PostgresExporter] Failed to get row count for {}", tableName, e);
            return 0;
        }
    }

    /**
     * 파티션 테이블명 검증 (SQL 인젝션 방지)
     *
     * <p>허용되는 형식: game_log_YYYY_wWW
     *
     * @param tableName 검증할 테이블명
     * @throws IllegalArgumentException 형식이 맞지 않는 경우
     */
    private void validatePartitionTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }

        if (!PARTITION_NAME_PATTERN.matcher(tableName).matches()) {
            log.error(
                    "[PostgresExporter] Invalid table name detected: '{}'. "
                            + "Possible SQL injection attempt.",
                    tableName);
            throw new IllegalArgumentException(
                    "Invalid table name: '" + tableName + "'. Expected format: game_log_YYYY_wWW");
        }
    }
}

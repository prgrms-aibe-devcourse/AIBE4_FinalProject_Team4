package kr.java.documind.domain.logprocessor.service.coldstorage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
     * PostgreSQL 테이블을 CSV 파일로 export
     *
     * @param tableName 테이블 이름
     * @param outputFile 출력 CSV 파일
     * @throws IOException 파일 I/O 오류
     */
    public void exportTableToCsv(String tableName, File outputFile) throws IOException {
        log.info("[PostgresExporter] Exporting table {} to {}", tableName, outputFile.getName());

        try (Connection connection = dataSource.getConnection();
                FileWriter writer = new FileWriter(outputFile)) {

            // PostgreSQL COPY 명령 사용
            CopyManager copyManager = new CopyManager((BaseConnection) connection);

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
     * @param tableName 테이블 이름
     * @return 행 개수
     */
    public long getTableRowCount(String tableName) {
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
}

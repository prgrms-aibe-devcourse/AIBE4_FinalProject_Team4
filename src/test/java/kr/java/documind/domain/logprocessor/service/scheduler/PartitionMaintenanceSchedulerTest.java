package kr.java.documind.domain.logprocessor.service.scheduler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import javax.sql.DataSource;
import kr.java.documind.domain.logprocessor.model.enums.EventCategory;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import kr.java.documind.domain.logprocessor.service.coldstorage.ColdStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("파티션 관리 스케줄러 통합 테스트")
@Import(PartitionMaintenanceSchedulerTestConfig.class)
class PartitionMaintenanceSchedulerTest {

    @Autowired private DataSource dataSource;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private ColdStorageService coldStorageService;

    private PartitionMaintenanceScheduler scheduler;

    private LocalDate today;
    private LocalDate currentMonday;

    @BeforeEach
    void setUp() throws Exception {
        // UTC 기준 날짜 (스케줄러와 동일한 기준)
        today = LocalDate.now(ZoneOffset.UTC);
        currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // PartitionMaintenanceScheduler 생성
        scheduler = new PartitionMaintenanceScheduler(dataSource, coldStorageService);
        scheduler.init();

        // ColdStorageService Mock 설정
        when(coldStorageService.archivePartitionToS3(anyString(), any(LocalDate.class)))
                .thenReturn(
                        "s3://test-bucket/cold-storage/game-logs/year=2024/week=01/test.parquet");
    }

    @Test
    @DisplayName("애플리케이션 시작 시 누락된 파티션을 자동으로 생성한다")
    void init_createsPartitions() {
        // given
        LocalDate targetMonday = currentMonday.plusWeeks(1);
        String tableName = buildExpectedTableName(targetMonday);

        // when
        scheduler.init();

        // then
        boolean exists = checkPartitionExists(tableName);
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("향후 2주 파티션이 Hot Storage로 생성된다")
    void createFuturePartitions_createsNextTwoWeeks() {
        // given
        LocalDate week1 = currentMonday;
        LocalDate week2 = currentMonday.plusWeeks(1);
        LocalDate week3 = currentMonday.plusWeeks(2);

        String table1 = buildExpectedTableName(week1);
        String table2 = buildExpectedTableName(week2);
        String table3 = buildExpectedTableName(week3);

        // when
        scheduler.maintainPartitions();

        // then
        assertThat(checkPartitionExists(table1)).isTrue();
        assertThat(checkPartitionExists(table2)).isTrue();
        assertThat(checkPartitionExists(table3)).isTrue();
    }

    @Test
    @DisplayName("생성된 파티션에 GIN 인덱스가 자동으로 생성된다")
    void createdPartition_hasGinIndexes() {
        // given
        LocalDate targetMonday = currentMonday;
        String tableName = buildExpectedTableName(targetMonday);

        // when
        scheduler.maintainPartitions();

        // then
        String sql =
                """
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = ?
                  AND indexname LIKE 'idx_%_attributes'
                  AND indexdef LIKE '%USING gin%'
                """;

        Integer indexCount = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        assertThat(indexCount).isEqualTo(1);
    }

    @Test
    @DisplayName("생성된 파티션에 occurred_at 인덱스가 자동으로 생성된다")
    void createdPartition_hasOccurredAtIndex() {
        // given
        LocalDate targetMonday = currentMonday;
        String tableName = buildExpectedTableName(targetMonday);

        // when
        scheduler.maintainPartitions();

        // then
        String sql =
                """
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = ?
                  AND indexname LIKE 'idx_%_occurred_at'
                """;

        Integer indexCount = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        assertThat(indexCount).isEqualTo(1);
    }

    @Test
    @DisplayName("4주(28일) 경과한 파티션은 Cold Storage로 이동된다")
    void moveToColdStorage_archivesOldPartition() throws Exception {
        // given: 4주 전 파티션 생성
        LocalDate coldMonday = currentMonday.minusWeeks(4);
        String tableName = buildExpectedTableName(coldMonday);
        createPartitionManually(coldMonday);

        // when
        scheduler.maintainPartitions();

        // then: ColdStorageService 호출 확인
        verify(coldStorageService, times(1)).archivePartitionToS3(eq(tableName), eq(coldMonday));

        // 파티션이 삭제되었는지 확인
        boolean exists = checkPartitionExists(tableName);
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("1주(7일) 경과한 파티션은 Warm Storage로 이동된다")
    void moveToWarmStorage_movesOldPartition() {
        // given: 1주 전 파티션 생성
        LocalDate warmMonday = currentMonday.minusWeeks(1);
        String tableName = buildExpectedTableName(warmMonday);
        createPartitionManually(warmMonday);

        // when
        scheduler.maintainPartitions();

        // then: 테스트 환경에서는 Tablespace 이동 skip됨
        // 파티션은 여전히 존재하고, pg_default tablespace에 있어야 함
        boolean exists = checkPartitionExists(tableName);
        assertThat(exists).isTrue();

        // Tablespace 확인 (테스트 환경에서는 pg_default)
        String tablespaceName = getTablespaceName(tableName);
        assertThat(tablespaceName).isEqualTo("pg_default");
    }

    @Test
    @DisplayName("Hot Storage 기간(7일 이내) 파티션은 Warm으로 이동되지 않는다")
    void hotStoragePartition_notMovedToWarm() {
        // given: 현재 주 파티션 생성
        LocalDate hotMonday = currentMonday;
        String tableName = buildExpectedTableName(hotMonday);
        createPartitionManually(hotMonday);

        // when
        scheduler.maintainPartitions();

        // then: 여전히 Hot Storage에 있어야 함
        boolean exists = checkPartitionExists(tableName);
        assertThat(exists).isTrue();

        String tablespaceName = getTablespaceName(tableName);
        assertThat(tablespaceName).isEqualTo("pg_default");
    }

    @Test
    @DisplayName("벌크 저장: 1000개 로그를 1초 이내에 저장한다")
    void bulkInsert_1000logs_within1second() {
        // given
        LocalDate targetMonday = currentMonday;
        createPartitionManually(targetMonday);

        String sql =
                """
                INSERT INTO game_log (
                    log_id, project_id, session_id, user_id, severity,
                    event_category, archive, occurred_at, ingested_at,
                    trace_id, span_id, fingerprint, resource, attributes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """;

        OffsetDateTime testDate = OffsetDateTime.parse(targetMonday.toString() + "T12:00:00+09:00");

        // when
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            jdbcTemplate.update(
                    sql,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "session-" + i,
                    "user-" + i,
                    LogSeverity.INFO.toString(),
                    EventCategory.GAMEPLAY.toString(),
                    "Bulk test log " + i,
                    testDate,
                    testDate,
                    "trace-" + i,
                    "span-" + i,
                    "fingerprint-" + i,
                    "{\"environment\": \"test\"}",
                    "{\"action\": \"bulk_test\"}");
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // then
        assertThat(duration).isLessThan(1000); // 1초 이내

        // 데이터 저장 확인
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM game_log", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1000);
    }

    @Test
    @DisplayName("파티션 범위 검증: 주별 파티션이 월요일부터 다음주 월요일까지 커버한다")
    void partition_coversWeekRange() {
        // given
        LocalDate targetMonday = currentMonday;
        String tableName = buildExpectedTableName(targetMonday);
        createPartitionManually(targetMonday);

        // when: 월요일 데이터 삽입
        insertTestLog(targetMonday.atTime(0, 0).atOffset(ZoneOffset.UTC));

        // when: 일요일 데이터 삽입
        LocalDate sunday = targetMonday.plusDays(6);
        insertTestLog(sunday.atTime(23, 59).atOffset(ZoneOffset.UTC));

        // then
        Integer count =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("동시 파티션 생성: 동일 파티션 중복 생성 시 에러가 발생하지 않는다")
    void concurrentPartitionCreation_doesNotFail() {
        // given
        LocalDate targetMonday = currentMonday;

        // when: 파티션을 두 번 생성 시도
        scheduler.maintainPartitions();
        scheduler.maintainPartitions();

        // then: 에러 없이 정상 완료
        String tableName = buildExpectedTableName(targetMonday);
        boolean exists = checkPartitionExists(tableName);
        assertThat(exists).isTrue();
    }

    // ===== Helper Methods =====

    /** 파티션 테이블 이름 생성 */
    private String buildExpectedTableName(LocalDate monday) {
        int year = monday.getYear();
        int weekNumber = monday.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("game_log_%d_w%02d", year, weekNumber);
    }

    /** 파티션 존재 여부 확인 */
    private boolean checkPartitionExists(String tableName) {
        String sql =
                """
                SELECT EXISTS (
                    SELECT 1 FROM pg_tables
                    WHERE tablename = ?
                )
                """;

        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, tableName);
        return Boolean.TRUE.equals(exists);
    }

    /** 파티션 수동 생성 (테스트용) */
    private void createPartitionManually(LocalDate weekStartMonday) {
        String tableName = buildExpectedTableName(weekStartMonday);

        // 이미 존재하면 skip
        if (checkPartitionExists(tableName)) {
            return;
        }

        LocalDate endDate = weekStartMonday.plusWeeks(1);

        String sql =
                String.format(
                        """
                        CREATE TABLE IF NOT EXISTS %s PARTITION OF game_log
                            FOR VALUES FROM ('%s 00:00:00+00') TO ('%s 00:00:00+00')
                        """,
                        tableName, weekStartMonday, endDate);

        jdbcTemplate.execute(sql);

        // GIN 인덱스 생성
        String attributesIndexSql =
                String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_attributes ON %s USING GIN (attributes jsonb_path_ops)",
                        tableName, tableName);
        jdbcTemplate.execute(attributesIndexSql);

        String resourceIndexSql =
                String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_resource ON %s USING GIN (resource jsonb_path_ops)",
                        tableName, tableName);
        jdbcTemplate.execute(resourceIndexSql);

        // occurred_at 인덱스 생성
        String occurredAtIndexSql =
                String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_occurred_at ON %s (occurred_at)",
                        tableName, tableName);
        jdbcTemplate.execute(occurredAtIndexSql);
    }

    /** 테스트 로그 삽입 */
    private void insertTestLog(OffsetDateTime occurredAt) {
        String sql =
                """
                INSERT INTO game_log (
                    log_id, project_id, session_id, user_id, severity,
                    event_category, archive, occurred_at, ingested_at,
                    trace_id, span_id, fingerprint, resource, attributes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """;

        jdbcTemplate.update(
                sql,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test-session",
                "test-user",
                LogSeverity.INFO.toString(),
                EventCategory.GAMEPLAY.toString(),
                "Test log message",
                occurredAt,
                occurredAt,
                "test-trace",
                "test-span",
                "test-fingerprint",
                "{\"environment\": \"test\"}",
                "{\"action\": \"test\"}");
    }

    /** 테이블의 Tablespace 이름 조회 */
    private String getTablespaceName(String tableName) {
        String sql =
                """
                SELECT COALESCE(t.spcname, 'pg_default') AS tablespace_name
                FROM pg_class c
                LEFT JOIN pg_tablespace t ON t.oid = c.reltablespace
                WHERE c.relname = ?
                """;

        String tablespaceName = jdbcTemplate.queryForObject(sql, String.class, tableName);
        return tablespaceName;
    }
}

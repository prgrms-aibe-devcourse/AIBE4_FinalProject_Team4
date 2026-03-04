package kr.java.documind.domain.logprocessor.service.scheduler;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * game_log 테이블의 월별 파티션을 자동으로 생성하는 스케줄러
 *
 * <p>매월 1일 00:00에 실행되어 다음 달 파티션을 미리 생성함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartitionMaintenanceScheduler {

    private final DataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        this.jdbcTemplate = new JdbcTemplate(dataSource);

        // 애플리케이션 시작 시 누락된 파티션 자동 생성
        createMissingPartitions();
    }

    /**
     * 매월 1일 00:00에 실행: 다음 달 파티션 생성
     *
     * <p>현재 월 + 2개월까지 파티션을 미리 생성하여 데이터 유실 방지
     */
    @Scheduled(cron = "0 0 0 1 * *") // 매월 1일 00:00
    public void createNextMonthPartition() {
        try {
            // 현재 월 + 2개월 파티션 생성
            YearMonth currentMonth = YearMonth.now();
            YearMonth targetMonth = currentMonth.plusMonths(2);

            createPartitionIfNotExists(targetMonth);

            log.info(
                    "[Partition] Successfully created partition for {}-{}",
                    targetMonth.getYear(),
                    String.format("%02d", targetMonth.getMonthValue()));
        } catch (Exception e) {
            log.error("[Partition] Failed to create partition", e);
        }
    }

    /**
     * 누락된 파티션 자동 생성
     *
     * <p>현재 월 기준 -1개월 ~ +2개월 파티션을 확인하고 누락 시 생성
     */
    private void createMissingPartitions() {
        try {
            YearMonth currentMonth = YearMonth.now();

            // 과거 1개월 ~ 미래 2개월 파티션 생성
            for (int i = -1; i <= 2; i++) {
                YearMonth targetMonth = currentMonth.plusMonths(i);
                createPartitionIfNotExists(targetMonth);
            }

            log.info("[Partition] Missing partitions check completed");
        } catch (Exception e) {
            log.error("[Partition] Failed to create missing partitions", e);
        }
    }

    /**
     * 특정 월의 파티션이 존재하지 않으면 생성
     *
     * @param yearMonth 파티션을 생성할 년월
     */
    private void createPartitionIfNotExists(YearMonth yearMonth) {
        String tableName = buildPartitionTableName(yearMonth);

        // 파티션 존재 여부 확인
        boolean exists = checkPartitionExists(tableName);
        if (exists) {
            log.debug("[Partition] Partition already exists: {}", tableName);
            return;
        }

        // 파티션 생성
        createPartition(yearMonth, tableName);

        // GIN 인덱스 생성
        createGinIndexes(tableName);

        // occurred_at 인덱스 생성
        createOccurredAtIndex(tableName);

        log.info("[Partition] Created new partition: {}", tableName);
    }

    /**
     * 파티션 테이블 이름 생성
     *
     * @param yearMonth 년월
     * @return 파티션 테이블 이름 (예: game_log_2024_03)
     */
    private String buildPartitionTableName(YearMonth yearMonth) {
        return String.format("game_log_%d_%02d", yearMonth.getYear(), yearMonth.getMonthValue());
    }

    /**
     * 파티션 존재 여부 확인
     *
     * @param tableName 파티션 테이블 이름
     * @return 존재 여부
     */
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

    /**
     * 파티션 생성
     *
     * @param yearMonth 년월
     * @param tableName 파티션 테이블 이름
     */
    private void createPartition(YearMonth yearMonth, String tableName) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.plusMonths(1).atDay(1);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String sql =
                String.format(
                        """
                        CREATE TABLE %s PARTITION OF game_log
                            FOR VALUES FROM ('%s 00:00:00+00') TO ('%s 00:00:00+00')
                        """,
                        tableName, startDate.format(formatter), endDate.format(formatter));

        jdbcTemplate.execute(sql);
        log.debug("[Partition] Created partition table: {}", tableName);
    }

    /**
     * GIN 인덱스 생성 (attributes, resource)
     *
     * @param tableName 파티션 테이블 이름
     */
    private void createGinIndexes(String tableName) {
        // attributes 컬럼 GIN 인덱스
        String attributesIndexSql =
                String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_attributes ON %s USING GIN (attributes"
                                + " jsonb_path_ops)",
                        tableName, tableName);
        jdbcTemplate.execute(attributesIndexSql);

        // resource 컬럼 GIN 인덱스
        String resourceIndexSql =
                String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_resource ON %s USING GIN (resource"
                                + " jsonb_path_ops)",
                        tableName, tableName);
        jdbcTemplate.execute(resourceIndexSql);

        log.debug("[Partition] Created GIN indexes for: {}", tableName);
    }

    /**
     * occurred_at 인덱스 생성 (시간 범위 쿼리 최적화)
     *
     * @param tableName 파티션 테이블 이름
     */
    private void createOccurredAtIndex(String tableName) {
        String sql =
                String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_occurred_at ON %s (occurred_at)",
                        tableName, tableName);
        jdbcTemplate.execute(sql);

        log.debug("[Partition] Created occurred_at index for: {}", tableName);
    }

    /**
     * 오래된 파티션 삭제 (30일 초과)
     *
     * <p>매월 1일 01:00에 실행: 30일 이전 파티션 삭제 (3-Tier 전략의 일부)
     */
    @Scheduled(cron = "0 0 1 1 * *") // 매월 1일 01:00
    public void dropOldPartitions() {
        try {
            YearMonth currentMonth = YearMonth.now();
            YearMonth oldMonth = currentMonth.minusMonths(2); // 2개월 이전 파티션 삭제

            String tableName = buildPartitionTableName(oldMonth);

            // 파티션 존재 확인
            boolean exists = checkPartitionExists(tableName);
            if (!exists) {
                log.debug("[Partition] Partition not found for deletion: {}", tableName);
                return;
            }

            // TODO: S3로 Parquet 덤프 후 삭제 (3-Tier 전략)
            // 현재는 단순 삭제만 수행
            String sql = String.format("DROP TABLE IF EXISTS %s", tableName);
            jdbcTemplate.execute(sql);

            log.warn("[Partition] Dropped old partition: {}", tableName);
        } catch (Exception e) {
            log.error("[Partition] Failed to drop old partitions", e);
        }
    }
}

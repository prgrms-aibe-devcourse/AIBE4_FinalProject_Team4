package kr.java.documind.domain.logprocessor.service.scheduler;

import jakarta.annotation.PostConstruct;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import javax.sql.DataSource;
import kr.java.documind.domain.logprocessor.service.coldstorage.ColdStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * game_log 테이블의 주별 파티션을 자동으로 생성/관리하는 스케줄러
 *
 * <p>3-Tier 저장 전략 (Tablespace 분리):
 *
 * <ul>
 *   <li>Hot Storage (SSD, 0~7일): 최신 데이터, 전체 인덱스, 빠른 쓰기/읽기
 *   <li>Warm Storage (HDD, 7~28일): 중간 데이터, Tablespace 이동으로 저비용화
 *   <li>Cold Storage (S3, 28일+): 오래된 데이터, Parquet 압축, Athena 쿼리
 * </ul>
 *
 * <p>스케줄링: 매주 월요일 00:00에 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartitionMaintenanceScheduler {

    private final DataSource dataSource;
    private final ColdStorageService coldStorageService;
    private final Environment environment;
    private JdbcTemplate jdbcTemplate;

    /** Hot Storage: 최근 1주 (7일) - SSD */
    private static final int HOT_STORAGE_WEEKS = 1;

    /** Warm Storage: 1~4주 (7~28일) - HDD */
    private static final int WARM_STORAGE_WEEKS = 4;

    /** Hot Storage Tablespace 이름 */
    private static final String HOT_TABLESPACE = "hot_storage";

    /** Warm Storage Tablespace 이름 */
    private static final String WARM_TABLESPACE = "warm_storage";

    /** 기본 Tablespace (개발 환경) */
    private static final String DEFAULT_TABLESPACE = "pg_default";

    @PostConstruct
    public void init() {
        this.jdbcTemplate = new JdbcTemplate(dataSource);

        // 애플리케이션 시작 시 누락된 파티션 자동 생성
        createMissingPartitions();

        // Tablespace 환경 로깅
        logTablespaceConfiguration();
    }

    /**
     * 매주 월요일 00:00 UTC에 실행: 3-Tier 저장 전략 실행
     *
     * <p><b>스케줄링:</b> UTC 기준 (서버 타임존 무관)
     *
     * <p><b>파티션 경계:</b> UTC +00 기준으로 정의되어 있으므로, 스케줄러도 UTC 기준으로 동작
     *
     * <ol>
     *   <li>향후 2주 파티션 생성 (Hot Storage)
     *   <li>1주 경과 파티션 → Warm Storage 이동 (SSD → HDD)
     *   <li>4주 경과 파티션 → Cold Storage 이동 (HDD → S3)
     * </ol>
     */
    @Scheduled(cron = "0 0 0 * * MON", zone = "UTC") // 매주 월요일 00:00 UTC
    public void maintainPartitions() {
        createFuturePartitions();
        moveToWarmStorage();
        moveToColdStorage();
    }

    /**
     * 파티션 생성 (Hot Storage)
     *
     * <p>데이터 유실 방지를 위해 과거 1주 ~ 미래 2주까지 파티션을 생성
     *
     * <p><b>과거 1주 포함 이유:</b> 로그 수집 시 (서버 시간 - 24h) ~ (서버 시간 + 1h) 허용하므로, 월요일 00:00 이후 24시간 전 로그(지난
     * 주 일요일)가 유입될 수 있음
     *
     * <p><b>타임존:</b> UTC 기준으로 동작 (파티션 경계와 일치)
     */
    private void createFuturePartitions() {
        try {
            // UTC 기준 날짜 (파티션 경계는 UTC +00으로 정의됨)
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            // 과거 1주 ~ 미래 2주까지 생성 (24시간 전 로그 대응)
            for (int i = -1; i <= 2; i++) {
                LocalDate targetMonday = monday.plusWeeks(i);
                createPartitionIfNotExists(targetMonday);
            }

            log.info(
                    "[Partition] Successfully created partitions (Past 1 week ~ Future 2 weeks for"
                            + " 24h log tolerance)");
        } catch (Exception e) {
            log.error("[Partition] Failed to create partitions", e);
        }
    }

    /**
     * 1주(7일) 경과한 파티션을 Warm Storage로 이동
     *
     * <p>Tablespace 이동: SSD → HDD
     *
     * <p><b>타임존:</b> UTC 기준으로 동작 (파티션 경계와 일치)
     *
     * <ul>
     *   <li>개발 환경: Tablespace 이동 skip (pg_default 유지)
     *   <li>운영 환경: ALTER TABLE ... SET TABLESPACE warm_storage
     * </ul>
     */
    private void moveToWarmStorage() {
        try {
            // UTC 기준 날짜 (파티션 경계는 UTC +00으로 정의됨)
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate warmMonday = monday.minusWeeks(HOT_STORAGE_WEEKS);

            // ISO week-based year 사용 (연말/연초 경계 처리)
            int year = warmMonday.get(IsoFields.WEEK_BASED_YEAR);
            int weekNumber = warmMonday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            String tableName = buildPartitionTableName(year, weekNumber);

            // 파티션 존재 확인
            boolean exists = checkPartitionExists(tableName);
            if (!exists) {
                log.debug("[Partition] Partition not found for Warm transition: {}", tableName);
                return;
            }

            // 이미 Warm Storage인지 확인
            if (isWarmStorage(tableName)) {
                log.debug("[Partition] Already in Warm Storage: {}", tableName);
                return;
            }

            // Warm Storage로 이동
            moveTableToWarmTablespace(tableName);

            log.info("[Partition] ⚡ Moved to Warm Storage: {} (7+ days old, SSD→HDD)", tableName);
        } catch (Exception e) {
            log.error("[Partition] Failed to move partition to Warm Storage", e);
        }
    }

    /**
     * 4주(28일) 경과한 파티션을 Cold Storage(S3)로 이동
     *
     * <p><b>타임존:</b> UTC 기준으로 동작 (파티션 경계와 일치)
     *
     * <p>Cold Storage 이동:
     *
     * <ol>
     *   <li>PostgreSQL → Parquet 파일 export
     *   <li>S3 업로드
     *   <li>Glue 카탈로그 등록 (Athena 쿼리용)
     *   <li>PostgreSQL 파티션 삭제
     * </ol>
     */
    private void moveToColdStorage() {
        try {
            // UTC 기준 날짜 (파티션 경계는 UTC +00으로 정의됨)
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate coldMonday = monday.minusWeeks(WARM_STORAGE_WEEKS);

            // ISO week-based year 사용 (연말/연초 경계 처리)
            int year = coldMonday.get(IsoFields.WEEK_BASED_YEAR);
            int weekNumber = coldMonday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            String tableName = buildPartitionTableName(year, weekNumber);

            // 파티션 존재 확인
            boolean exists = checkPartitionExists(tableName);
            if (!exists) {
                log.debug("[Partition] Partition not found for Cold archival: {}", tableName);
                return;
            }

            // S3로 Parquet Export (모든 프로젝트가 성공해야 함)
            String s3Uri = coldStorageService.archivePartitionToS3(tableName, coldMonday);

            // 아카이빙 성공 시에만 파티션 삭제
            String sql = String.format("DROP TABLE IF EXISTS %s", tableName);
            jdbcTemplate.execute(sql);

            log.warn(
                    "[Partition] ❄️ Archived to Cold Storage: {} (28+ days old) → {}",
                    tableName,
                    s3Uri);
        } catch (Exception e) {
            log.error("[Partition] Failed to move partition to Cold Storage", e);
        }
    }

    /**
     * 파티션을 Warm Storage Tablespace로 이동
     *
     * <p>운영 환경: SSD → HDD 이동 개발 환경: skip (pg_default 유지)
     *
     * @param tableName 파티션 테이블 이름
     */
    private void moveTableToWarmTablespace(String tableName) {
        try {
            // 개발 환경에서는 Tablespace 이동 skip
            if (isDevEnvironment()) {
                log.debug("[Partition] Skip Tablespace move in dev environment: {}", tableName);
                return;
            }

            // Warm Tablespace 존재 확인
            if (!checkTablespaceExists(WARM_TABLESPACE)) {
                log.warn(
                        "[Partition] Warm tablespace not found: {}. Skipping move.",
                        WARM_TABLESPACE);
                return;
            }

            // Tablespace 이동
            String sql =
                    String.format("ALTER TABLE %s SET TABLESPACE %s", tableName, WARM_TABLESPACE);
            jdbcTemplate.execute(sql);

            log.info("[Partition] Moved {} to Warm tablespace (SSD→HDD)", tableName);
        } catch (Exception e) {
            log.error("[Partition] Failed to move table to Warm tablespace: {}", tableName, e);
            throw e;
        }
    }

    /**
     * 파티션이 Warm Storage에 있는지 확인
     *
     * @param tableName 파티션 테이블 이름
     * @return Warm Storage 여부
     */
    private boolean isWarmStorage(String tableName) {
        try {
            // 개발 환경에서는 항상 false (Tablespace 이동 안 함)
            if (isDevEnvironment()) {
                return false;
            }

            String sql =
                    """
                    SELECT EXISTS (
                        SELECT 1 FROM pg_class c
                        JOIN pg_tablespace t ON t.oid = c.reltablespace
                        WHERE c.relname = ?
                        AND t.spcname = ?
                    )
                    """;

            Boolean isWarm =
                    jdbcTemplate.queryForObject(sql, Boolean.class, tableName, WARM_TABLESPACE);
            return Boolean.TRUE.equals(isWarm);
        } catch (Exception e) {
            log.debug("[Partition] Failed to check Warm Storage status: {}", tableName, e);
            return false;
        }
    }

    /**
     * Tablespace 존재 여부 확인
     *
     * @param tablespaceName Tablespace 이름
     * @return 존재 여부
     */
    private boolean checkTablespaceExists(String tablespaceName) {
        try {
            String sql = "SELECT COUNT(*) FROM pg_tablespace WHERE spcname = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tablespaceName);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("[Partition] Failed to check tablespace existence: {}", tablespaceName, e);
            return false;
        }
    }

    /**
     * 개발 환경 여부 확인
     *
     * <p>다중 프로파일 지원 (예: "dev,mysql" → true)
     *
     * @return 개발 환경이면 true
     */
    private boolean isDevEnvironment() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(
                        profile ->
                                "dev".equals(profile)
                                        || "local".equals(profile)
                                        || "test".equals(profile));
    }

    /** Tablespace 설정 로깅 */
    private void logTablespaceConfiguration() {
        if (isDevEnvironment()) {
            log.info(
                    """
                    ┌─────────────────────────────────────────────┐
                    │  [DEV] 3-Tier Tablespace Configuration      │
                    ├─────────────────────────────────────────────┤
                    │  Hot:  pg_default (개발 환경)               │
                    │  Warm: pg_default (개발 환경)               │
                    │  운영 배포 시 Tablespace 설정 필요          │
                    └─────────────────────────────────────────────┘
                    """);
        } else {
            boolean hotExists = checkTablespaceExists(HOT_TABLESPACE);
            boolean warmExists = checkTablespaceExists(WARM_TABLESPACE);

            log.info(
                    """
                    ┌─────────────────────────────────────────────┐
                    │  [PROD] 3-Tier Tablespace Configuration     │
                    ├─────────────────────────────────────────────┤
                    │  Hot:  {} (존재: {})                         │
                    │  Warm: {} (존재: {})                         │
                    └─────────────────────────────────────────────┘
                    """,
                    HOT_TABLESPACE,
                    hotExists ? "✅" : "❌",
                    WARM_TABLESPACE,
                    warmExists ? "✅" : "❌");
        }
    }

    /**
     * 누락된 파티션 자동 생성
     *
     * <p>현재 주 기준 -1주 ~ +2주 파티션을 확인하고 누락 시 생성
     *
     * <p><b>타임존:</b> UTC 기준으로 동작 (파티션 경계와 일치)
     */
    private void createMissingPartitions() {
        try {
            // UTC 기준 날짜 (파티션 경계는 UTC +00으로 정의됨)
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            // 과거 1주 ~ 미래 2주 파티션 생성
            for (int i = -1; i <= 2; i++) {
                LocalDate targetMonday = monday.plusWeeks(i);
                createPartitionIfNotExists(targetMonday);
            }

            log.info("[Partition] Missing partitions check completed");
        } catch (Exception e) {
            log.error("[Partition] Failed to create missing partitions", e);
        }
    }

    /**
     * 특정 주의 파티션이 존재하지 않으면 생성
     *
     * @param weekStartMonday 파티션 시작 월요일 날짜
     */
    private void createPartitionIfNotExists(LocalDate weekStartMonday) {
        // ISO week-based year 사용 (연말/연초 경계 처리)
        int year = weekStartMonday.get(IsoFields.WEEK_BASED_YEAR);
        int weekNumber = weekStartMonday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        String tableName = buildPartitionTableName(year, weekNumber);

        // 파티션 존재 여부 확인
        boolean exists = checkPartitionExists(tableName);
        if (exists) {
            log.debug("[Partition] Partition already exists: {}", tableName);
            return;
        }

        // 파티션 생성
        createPartition(weekStartMonday, tableName);

        // GIN 인덱스 생성
        createGinIndexes(tableName);

        // occurred_at 인덱스 생성 (Hot Storage용)
        createOccurredAtIndex(tableName);

        log.info("[Partition] Created new weekly partition (Hot Storage): {}", tableName);
    }

    /**
     * 파티션 테이블 이름 생성 (ISO 주차 기준)
     *
     * @param year 연도
     * @param weekNumber ISO 주차 번호 (1~53)
     * @return 파티션 테이블 이름 (예: game_log_2024_w10)
     */
    private String buildPartitionTableName(int year, int weekNumber) {
        return String.format("game_log_%d_w%02d", year, weekNumber);
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
     * 파티션 생성 (주별)
     *
     * <p>동시성 안전: 파티션 존재 여부를 먼저 확인하여 예외 메시지 의존성 제거
     *
     * @param weekStartMonday 주 시작 월요일
     * @param tableName 파티션 테이블 이름
     */
    private void createPartition(LocalDate weekStartMonday, String tableName) {
        // 1. 파티션 존재 여부 사전 확인 (멀티 인스턴스 환경 안전성)
        if (checkPartitionExists(tableName)) {
            log.debug("[Partition] Partition already exists: {}", tableName);
            return;
        }

        LocalDate startDate = weekStartMonday;
        LocalDate endDate = weekStartMonday.plusWeeks(1); // 다음 주 월요일

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String sql =
                String.format(
                        """
                        CREATE TABLE %s PARTITION OF game_log
                            FOR VALUES FROM ('%s 00:00:00+00') TO ('%s 00:00:00+00')
                        """,
                        tableName, startDate.format(formatter), endDate.format(formatter));

        try {
            jdbcTemplate.execute(sql);
            log.debug("[Partition] Created partition table: {}", tableName);
        } catch (Exception e) {
            // 2. 동시 생성 경쟁 상황 (race condition) - 재확인
            if (checkPartitionExists(tableName)) {
                log.debug(
                        "[Partition] Partition created by another instance during race condition: {}",
                        tableName);
                return;
            }

            // 3. 실제 에러 - 예외 전파
            log.error("[Partition] Failed to create partition table: {}", tableName, e);
            throw e;
        }
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
}

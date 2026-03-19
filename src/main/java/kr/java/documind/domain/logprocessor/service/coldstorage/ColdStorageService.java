package kr.java.documind.domain.logprocessor.service.coldstorage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Cold Storage 서비스
 *
 * <p>28일 이상 경과한 파티션을 S3에 Parquet 형식으로 아카이빙
 *
 * <p>주요 기능:
 *
 * <ul>
 *   <li>PostgreSQL 파티션 → CSV export
 *   <li>CSV → Parquet 변환
 *   <li>Parquet → S3 업로드
 *   <li>로컬 임시 파일 정리
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColdStorageService {

    private final S3Client s3Client;
    private final ParquetExporter parquetExporter;
    private final PostgresExporter postgresExporter;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String s3Bucket;

    @Value("${cold-storage.s3-prefix:cold-storage/game-logs}")
    private String s3Prefix;

    @Value("${cold-storage.temp-dir:${java.io.tmpdir}/cold-storage}")
    private String tempDir;

    /**
     * 파티션 테이블명 검증 패턴 (SQL 인젝션 방지)
     *
     * <p>허용 형식: game_log_YYYY_wWW (예: game_log_2024_w10)
     *
     * <p>주차: 01-53만 허용 (ISO 8601 week numbering)
     */
    private static final Pattern PARTITION_NAME_PATTERN =
            Pattern.compile("^game_log_\\d{4}_w(0[1-9]|[1-4][0-9]|5[0-3])$");

    /**
     * 파티션을 Cold Storage(S3)로 아카이빙 (프로젝트별로 분리)
     *
     * <p><b>접근 제한:</b> 스케줄러 전용 (외부 호출 금지)
     *
     * <p><b>보안:</b> SQL 인젝션 방지를 위한 테이블명 검증
     *
     * <p><b>경고:</b> tableName은 내부에서 생성된 값만 사용 가능. 외부 입력 금지!
     *
     * <p><b>Multi-tenancy:</b> 각 프로젝트의 데이터를 회사별/프로젝트별로 분리하여 S3에 저장
     *
     * @param tableName 파티션 테이블 이름 (예: game_log_2024_w10)
     * @param weekStartDate 주 시작 날짜
     * @return 업로드된 S3 URI 목록 (프로젝트별로 하나씩)
     * @throws IOException 파일 I/O 오류
     * @throws IllegalArgumentException 잘못된 테이블명 (SQL 인젝션 시도 등)
     */
    public String archivePartitionToS3(String tableName, LocalDate weekStartDate)
            throws IOException {
        log.info("[ColdStorage] Starting archive process for table: {}", tableName);

        // SQL 인젝션 방지: 테이블명 검증
        validatePartitionTableName(tableName);

        // 1. 파티션에서 고유한 project_id 목록 조회
        List<UUID> projectIds = postgresExporter.getDistinctProjectIds(tableName);
        log.info("[ColdStorage] Found {} projects in partition {}", projectIds.size(), tableName);

        if (projectIds.isEmpty()) {
            log.warn("[ColdStorage] No data found in partition {}, skipping archive", tableName);
            return "No data to archive";
        }

        // 2. 각 프로젝트별로 아카이빙
        List<String> s3Uris = new ArrayList<>();
        List<UUID> failedProjects = new ArrayList<>();

        for (UUID projectId : projectIds) {
            try {
                String s3Uri = archiveProjectData(tableName, projectId, weekStartDate);
                s3Uris.add(s3Uri);
            } catch (Exception e) {
                log.error(
                        "[ColdStorage] Failed to archive project {} from partition {}",
                        projectId,
                        tableName,
                        e);
                failedProjects.add(projectId);
            }
        }

        // 실패한 프로젝트가 있으면 예외 발생 (파티션 삭제 방지)
        if (!failedProjects.isEmpty()) {
            String errorMsg =
                    String.format(
                            "Failed to archive %d out of %d projects from %s. Failed projects: %s",
                            failedProjects.size(), projectIds.size(), tableName, failedProjects);
            log.error("[ColdStorage] {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }

        log.info(
                "[ColdStorage] Successfully archived all {} projects from {}",
                s3Uris.size(),
                tableName);

        return String.join(", ", s3Uris);
    }

    /**
     * 특정 프로젝트의 데이터를 S3로 아카이빙
     *
     * @param tableName 파티션 테이블 이름
     * @param projectId 프로젝트 ID
     * @param weekStartDate 주 시작 날짜
     * @return S3 URI
     * @throws IOException 파일 I/O 오류
     */
    private String archiveProjectData(String tableName, UUID projectId, LocalDate weekStartDate)
            throws IOException {
        log.info("[ColdStorage] Archiving project {} from {}", projectId, tableName);

        // 1. company_id 조회
        Long companyId = postgresExporter.getCompanyIdForProject(projectId);

        // 2. 고유한 임시 디렉토리 생성
        Path uniqueTempDir = createUniqueTempDirectory(tableName + "_" + projectId);

        try {
            // 3. PostgreSQL → CSV export (프로젝트별)
            File csvFile = exportProjectToCsv(tableName, projectId, uniqueTempDir);

            // 4. CSV → Parquet 변환
            File parquetFile =
                    convertCsvToParquet(csvFile, "game_log_" + projectId, uniqueTempDir);

            // 5. Parquet → S3 업로드
            String s3Uri =
                    uploadParquetToS3(parquetFile, companyId, projectId, weekStartDate);

            log.info("[ColdStorage] Successfully archived project {} to {}", projectId, s3Uri);

            return s3Uri;

        } finally {
            // 6. 임시 파일 정리
            cleanupTempDirectory(uniqueTempDir);
        }
    }

    /**
     * 고유한 임시 디렉토리 생성 (동시 실행 안전성)
     *
     * @param tableName 테이블 이름
     * @return 고유 임시 디렉토리 경로
     */
    private Path createUniqueTempDirectory(String tableName) throws IOException {
        // 타임스탬프 기반 고유 디렉토리 생성 (동시 실행 시 충돌 방지)
        String timestamp = String.valueOf(System.currentTimeMillis());
        Path baseTempDir = Path.of(tempDir);
        Path uniqueTempDir = baseTempDir.resolve(tableName + "_" + timestamp);

        Files.createDirectories(uniqueTempDir);
        log.debug("[ColdStorage] Created unique temp directory: {}", uniqueTempDir);

        return uniqueTempDir;
    }

    /**
     * PostgreSQL 파티션에서 특정 프로젝트 데이터만 CSV로 export
     *
     * @param tableName 파티션 테이블 이름
     * @param projectId 프로젝트 ID
     * @param tempDirPath 임시 디렉토리
     * @return CSV 파일
     */
    private File exportProjectToCsv(String tableName, UUID projectId, Path tempDirPath)
            throws IOException {
        log.info("[ColdStorage] Exporting {} (project: {}) to CSV...", tableName, projectId);

        File csvFile = tempDirPath.resolve(tableName + "_" + projectId + ".csv").toFile();

        // PostgreSQL COPY TO를 사용하여 프로젝트별 CSV export
        postgresExporter.exportTableToCsvByProject(tableName, projectId, csvFile);

        long fileSizeMB = csvFile.length() / (1024 * 1024);
        log.info(
                "[ColdStorage] CSV export completed: {} ({} MB)",
                csvFile.getName(),
                fileSizeMB);

        return csvFile;
    }

    /**
     * CSV → Parquet 변환
     *
     * @param csvFile CSV 파일
     * @param tableName 테이블 이름
     * @param tempDirPath 임시 디렉토리
     * @return Parquet 파일
     */
    private File convertCsvToParquet(File csvFile, String tableName, Path tempDirPath)
            throws IOException {
        log.info("[ColdStorage] Converting CSV to Parquet...");

        File parquetFile = tempDirPath.resolve(tableName + ".parquet").toFile();

        // CSV → Parquet 변환
        parquetExporter.convertCsvToParquet(csvFile, parquetFile);

        long originalSizeMB = csvFile.length() / (1024 * 1024);
        long compressedSizeMB = parquetFile.length() / (1024 * 1024);

        // CSV 파일이 비어있으면 압축률 계산 불가 (divide by zero 방지)
        double compressionRatio = 0.0;
        if (csvFile.length() > 0) {
            compressionRatio = (1 - (double) parquetFile.length() / csvFile.length()) * 100;
            log.info(
                    "[ColdStorage] Parquet conversion completed: {} MB → {} MB ({}% compression)",
                    originalSizeMB, compressedSizeMB, String.format("%.1f", compressionRatio));
        } else {
            log.warn(
                    "[ColdStorage] CSV file is empty, skipping compression ratio calculation. tableName={}",
                    tableName);
        }

        return parquetFile;
    }

    /**
     * Parquet → S3 업로드 (Multi-tenancy 지원)
     *
     * <p>S3 경로: {s3Prefix}/{companyId}/{projectId}/year=YYYY/week=WW/game_log.parquet
     *
     * @param parquetFile Parquet 파일
     * @param companyId 회사 ID
     * @param projectId 프로젝트 ID
     * @param weekStartDate 주 시작 날짜
     * @return S3 URI
     */
    private String uploadParquetToS3(
            File parquetFile, Long companyId, UUID projectId, LocalDate weekStartDate) {
        log.info(
                "[ColdStorage] Uploading Parquet to S3 (company: {}, project: {})...",
                companyId,
                projectId);

        // S3 키 생성: cold-storage/game-logs/{companyId}/{projectId}/year=2026/week=11/game_log.parquet
        // ISO week-based year 사용 (연말/연초 경계 처리)
        int year = weekStartDate.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
        int weekNumber = weekStartDate.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        String s3Key =
                String.format(
                        "%s/%d/%s/year=%d/week=%02d/game_log.parquet",
                        s3Prefix, companyId, projectId, year, weekNumber);

        // S3 업로드
        PutObjectRequest putRequest =
                PutObjectRequest.builder()
                        .bucket(s3Bucket)
                        .key(s3Key)
                        .contentType("application/octet-stream")
                        .metadata(
                                java.util.Map.of(
                                        "company-id", companyId.toString(),
                                        "project-id", projectId.toString(),
                                        "week-start-date", weekStartDate.toString(),
                                        "archived-at",
                                                java.time.OffsetDateTime.now(
                                                                java.time.ZoneOffset.UTC)
                                                        .toString()))
                        .build();

        s3Client.putObject(putRequest, parquetFile.toPath());

        String s3Uri = String.format("s3://%s/%s", s3Bucket, s3Key);
        log.info("[ColdStorage] S3 upload completed: {}", s3Uri);

        return s3Uri;
    }

    /** 임시 파일 정리 */
    private void cleanupTempDirectory(Path tempDirPath) {
        try {
            Files.walk(tempDirPath)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);

            log.debug("[ColdStorage] Cleaned up temp directory: {}", tempDirPath);
        } catch (IOException e) {
            log.warn("[ColdStorage] Failed to cleanup temp directory: {}", tempDirPath, e);
        }
    }

    /**
     * Cold Storage에서 데이터 복원
     *
     * <p><b>접근 제한:</b> 운영진 전용 (향후 @PreAuthorize("hasRole('ADMIN')") 추가 필요)
     *
     * <p><b>보안:</b> SQL 인젝션 방지를 위한 테이블명 검증
     *
     * <p><b>경고:</b> tableName은 서버에서 생성된 값만 사용 가능. 사용자 입력 금지!
     *
     * <p><b>사용 시나리오:</b>
     *
     * <ul>
     *   <li>데이터 재처리 (버그 수정 후)
     *   <li>규정 준수 감사
     *   <li>긴급 복구
     * </ul>
     *
     * @param tableName 파티션 테이블 이름
     * @param weekStartDate 주 시작 날짜
     * @throws IllegalArgumentException 잘못된 테이블명 (SQL 인젝션 시도 등)
     */
    public void restoreFromS3(String tableName, LocalDate weekStartDate) {
        log.info("[ColdStorage] Starting restore process for table: {}", tableName);

        // SQL 인젝션 방지: 테이블명 검증
        validatePartitionTableName(tableName);

        // TODO: S3 Parquet → PostgreSQL 복원 로직 구현
        // 1. S3에서 Parquet 다운로드
        // 2. Parquet → CSV 변환
        // 3. PostgreSQL 파티션 재생성
        // 4. CSV → PostgreSQL COPY
        log.warn(
                "[ColdStorage] Restore functionality not yet implemented: {} (week: {})",
                tableName,
                weekStartDate);
    }

    /**
     * 파티션 테이블명 검증 (SQL 인젝션 방지)
     *
     * <p>허용되는 형식: game_log_YYYY_wWW
     *
     * <p>예시:
     *
     * <ul>
     *   <li>✅ game_log_2024_w10
     *   <li>✅ game_log_2023_w52
     *   <li>❌ game_log_2024_w10; DROP TABLE users; --
     *   <li>❌ game_log_2024_w10 UNION SELECT * FROM passwords
     * </ul>
     *
     * @param tableName 검증할 테이블명
     * @throws IllegalArgumentException 형식이 맞지 않는 경우
     */
    private void validatePartitionTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("Partition table name cannot be null or empty");
        }

        if (!PARTITION_NAME_PATTERN.matcher(tableName).matches()) {
            log.error(
                    "[ColdStorage] Invalid partition table name detected: '{}'. "
                            + "Possible SQL injection attempt.",
                    tableName);
            throw new IllegalArgumentException(
                    "Invalid partition table name: '"
                            + tableName
                            + "'. Expected format: game_log_YYYY_wWW (e.g., game_log_2024_w10)");
        }
    }
}

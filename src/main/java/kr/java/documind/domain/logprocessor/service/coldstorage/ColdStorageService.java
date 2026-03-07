package kr.java.documind.domain.logprocessor.service.coldstorage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
     * 파티션을 Cold Storage(S3)로 아카이빙
     *
     * @param tableName 파티션 테이블 이름 (예: game_log_2024_w10)
     * @param weekStartDate 주 시작 날짜
     * @return 업로드된 S3 URI
     * @throws IOException 파일 I/O 오류
     */
    public String archivePartitionToS3(String tableName, LocalDate weekStartDate)
            throws IOException {
        log.info("[ColdStorage] Starting archive process for table: {}", tableName);

        // 1. 임시 디렉토리 생성
        Path tempDirPath = createTempDirectory();

        try {
            // 2. PostgreSQL → CSV export
            File csvFile = exportPartitionToCsv(tableName, tempDirPath);

            // 3. CSV → Parquet 변환
            File parquetFile = convertCsvToParquet(csvFile, tableName, tempDirPath);

            // 4. Parquet → S3 업로드
            String s3Uri = uploadParquetToS3(parquetFile, tableName, weekStartDate);

            log.info("[ColdStorage] Successfully archived {} to {}", tableName, s3Uri);

            return s3Uri;

        } finally {
            // 5. 임시 파일 정리
            cleanupTempDirectory(tempDirPath);
        }
    }

    /**
     * 임시 디렉토리 생성
     */
    private Path createTempDirectory() throws IOException {
        Path tempDirPath = Path.of(tempDir);
        if (!Files.exists(tempDirPath)) {
            Files.createDirectories(tempDirPath);
            log.debug("[ColdStorage] Created temp directory: {}", tempDirPath);
        }
        return tempDirPath;
    }

    /**
     * PostgreSQL 파티션 → CSV export
     * @param tableName 파티션 테이블 이름
     * @param tempDirPath 임시 디렉토리
     * @return CSV 파일
     */
    private File exportPartitionToCsv(String tableName, Path tempDirPath) throws IOException {
        log.info("[ColdStorage] Exporting {} to CSV...", tableName);

        File csvFile = tempDirPath.resolve(tableName + ".csv").toFile();

        // PostgreSQL COPY TO를 사용하여 CSV export
        postgresExporter.exportTableToCsv(tableName, csvFile);

        long fileSizeMB = csvFile.length() / (1024 * 1024);
        log.info("[ColdStorage] CSV export completed: {} ({} MB)", csvFile.getName(), fileSizeMB);

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
        double compressionRatio = (1 - (double) parquetFile.length() / csvFile.length()) * 100;

        log.info(
                "[ColdStorage] Parquet conversion completed: {} MB → {} MB ({}% compression)",
                originalSizeMB, compressedSizeMB, String.format("%.1f", compressionRatio));

        return parquetFile;
    }

    /**
     * Parquet → S3 업로드
     *
     * @param parquetFile Parquet 파일
     * @param tableName 테이블 이름
     * @param weekStartDate 주 시작 날짜
     * @return S3 URI
     */
    private String uploadParquetToS3(File parquetFile, String tableName, LocalDate weekStartDate) {
        log.info("[ColdStorage] Uploading Parquet to S3...");

        // S3 키 생성: cold-storage/game-logs/year=2024/week=10/game_log_2024_w10.parquet
        int year = weekStartDate.getYear();
        int weekNumber = weekStartDate.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        String s3Key =
                String.format(
                        "%s/year=%d/week=%02d/%s.parquet",
                        s3Prefix, year, weekNumber, tableName);

        // S3 업로드
        PutObjectRequest putRequest =
                PutObjectRequest.builder()
                        .bucket(s3Bucket)
                        .key(s3Key)
                        .contentType("application/octet-stream")
                        .metadata(
                                java.util.Map.of(
                                        "table-name", tableName,
                                        "week-start-date", weekStartDate.toString(),
                                        "archived-at",
                                                java.time.OffsetDateTime.now().toString()))
                        .build();

        s3Client.putObject(putRequest, parquetFile.toPath());

        String s3Uri = String.format("s3://%s/%s", s3Bucket, s3Key);
        log.info("[ColdStorage] S3 upload completed: {}", s3Uri);

        return s3Uri;
    }

    /**
     * 임시 파일 정리
     */
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
     * Cold Storage에서 데이터 복원 (향후 구현)
     *
     * @param tableName 파티션 테이블 이름
     * @param weekStartDate 주 시작 날짜
     */
    public void restoreFromS3(String tableName, LocalDate weekStartDate) {
        // TODO: S3 Parquet → PostgreSQL 복원 로직
        log.info(
                "[ColdStorage] TODO: Restore {} from S3 (week: {})",
                tableName, weekStartDate);
    }
}

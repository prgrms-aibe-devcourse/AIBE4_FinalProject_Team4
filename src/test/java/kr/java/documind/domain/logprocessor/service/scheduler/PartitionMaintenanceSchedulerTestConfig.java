package kr.java.documind.domain.logprocessor.service.scheduler;

import static org.mockito.Mockito.mock;

import kr.java.documind.domain.logprocessor.service.coldstorage.ColdStorageService;
import kr.java.documind.domain.logprocessor.service.coldstorage.ParquetExporter;
import kr.java.documind.domain.logprocessor.service.coldstorage.PostgresExporter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;

@TestConfiguration
public class PartitionMaintenanceSchedulerTestConfig {

    @Bean
    @Primary
    public S3Client s3Client() {
        return mock(S3Client.class);
    }

    @Bean
    @Primary
    public PostgresExporter postgresExporter() {
        return mock(PostgresExporter.class);
    }

    @Bean
    @Primary
    public ParquetExporter parquetExporter() {
        return mock(ParquetExporter.class);
    }

    @Bean
    @Primary
    public ColdStorageService coldStorageService() {
        return mock(ColdStorageService.class);
    }
}

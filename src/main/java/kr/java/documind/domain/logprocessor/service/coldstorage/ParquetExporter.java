package kr.java.documind.domain.logprocessor.service.coldstorage;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.springframework.stereotype.Component;

/**
 * Parquet 파일 변환 유틸리티
 *
 * <p>CSV 파일을 Apache Parquet 형식으로 변환
 *
 * <p>Parquet 특징:
 *
 * <ul>
 *   <li>컬럼 기반 저장 (Columnar Storage)
 *   <li>압축률 우수 (SNAPPY 압축)
 *   <li>Athena/Spark에서 직접 쿼리 가능
 * </ul>
 */
@Slf4j
@Component
public class ParquetExporter {

    /**
     * CSV 파일을 Parquet 파일로 변환
     *
     * @param csvFile 입력 CSV 파일
     * @param parquetFile 출력 Parquet 파일
     * @throws IOException 파일 I/O 오류
     */
    public void convertCsvToParquet(File csvFile, File parquetFile) throws IOException {
        log.info("[ParquetExporter] Converting {} to Parquet", csvFile.getName());

        // Avro 스키마 정의
        Schema schema = createGameLogSchema();

        // Parquet Writer 설정
        Configuration conf = new Configuration();
        Path outputPath = new Path(parquetFile.getAbsolutePath());

        try (ParquetWriter<GenericRecord> writer =
                        AvroParquetWriter.<GenericRecord>builder(outputPath)
                                .withSchema(schema)
                                .withConf(conf)
                                .withCompressionCodec(CompressionCodecName.SNAPPY)
                                .withPageSize(4 * 1024 * 1024) // 4MB (대용량 데이터에 최적화)
                                .withRowGroupSize(128 * 1024 * 1024) // 128MB
                                .build();
                Reader reader = new FileReader(csvFile);
                CSVParser csvParser =
                        CSVFormat.DEFAULT
                                .builder()
                                .setHeader()
                                .setSkipHeaderRecord(true)
                                .setQuote('"')
                                .setEscape('"') // PostgreSQL COPY CSV escape 처리
                                .build()
                                .parse(reader)) {

            long rowCount = 0;

            // CSV 데이터 읽기 및 Parquet 쓰기 (Apache Commons CSV 사용)
            for (CSVRecord csvRecord : csvParser) {
                GenericRecord record = parseCsvRecordToAvroRecord(csvRecord, schema);
                writer.write(record);
                rowCount++;

                if (rowCount % 10000 == 0) {
                    log.debug("[ParquetExporter] Processed {} rows", rowCount);
                }
            }

            log.info("[ParquetExporter] Parquet conversion completed: {} rows", rowCount);

        } catch (Exception e) {
            log.error("[ParquetExporter] Failed to convert CSV to Parquet", e);
            throw new IOException("Parquet conversion failed", e);
        }
    }

    /** GameLog 테이블 스키마 정의 (Avro Schema) */
    private Schema createGameLogSchema() {
        return SchemaBuilder.record("GameLog")
                .namespace("kr.java.documind.domain.logprocessor")
                .fields()
                .name("log_id")
                .type()
                .stringType()
                .noDefault()
                .name("project_id")
                .type()
                .stringType()
                .noDefault()
                .name("session_id")
                .type()
                .stringType()
                .noDefault()
                .name("user_id")
                .type()
                .nullable()
                .stringType()
                .noDefault()
                .name("severity")
                .type()
                .stringType()
                .noDefault()
                .name("event_category")
                .type()
                .stringType()
                .noDefault()
                .name("archive")
                .type()
                .stringType()
                .noDefault()
                .name("occurred_at")
                .type()
                .stringType()
                .noDefault()
                .name("ingested_at")
                .type()
                .stringType()
                .noDefault()
                .name("trace_id")
                .type()
                .nullable()
                .stringType()
                .noDefault()
                .name("span_id")
                .type()
                .nullable()
                .stringType()
                .noDefault()
                .name("fingerprint")
                .type()
                .stringType()
                .noDefault()
                .name("resource")
                .type()
                .stringType()
                .noDefault()
                .name("attributes")
                .type()
                .stringType()
                .noDefault()
                .name("created_at")
                .type()
                .stringType()
                .noDefault()
                .name("updated_at")
                .type()
                .stringType()
                .noDefault()
                .endRecord();
    }

    /**
     * CSVRecord를 Avro GenericRecord로 변환 (Apache Commons CSV 사용)
     *
     * @param csvRecord CSV 레코드
     * @param schema Avro 스키마
     * @return GenericRecord
     */
    private GenericRecord parseCsvRecordToAvroRecord(CSVRecord csvRecord, Schema schema) {
        GenericRecord record = new GenericData.Record(schema);

        // Apache Commons CSV가 자동으로 escape 처리 (""→")
        if (csvRecord.size() >= 16) {
            record.put("log_id", csvRecord.get(0));
            record.put("project_id", csvRecord.get(1));
            record.put("session_id", csvRecord.get(2));
            record.put("user_id", emptyToNull(csvRecord.get(3)));
            record.put("severity", csvRecord.get(4));
            record.put("event_category", csvRecord.get(5));
            record.put("archive", csvRecord.get(6));
            record.put("occurred_at", csvRecord.get(7));
            record.put("ingested_at", csvRecord.get(8));
            record.put("trace_id", emptyToNull(csvRecord.get(9)));
            record.put("span_id", emptyToNull(csvRecord.get(10)));
            record.put("fingerprint", csvRecord.get(11));
            record.put("resource", csvRecord.get(12));
            record.put("attributes", csvRecord.get(13));
            record.put("created_at", csvRecord.get(14));
            record.put("updated_at", csvRecord.get(15));
        }

        return record;
    }

    /**
     * 빈 문자열을 null로 변환 (nullable 필드 처리)
     *
     * @param value 문자열 값
     * @return null 또는 원래 값
     */
    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}

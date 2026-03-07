package kr.java.documind.domain.logprocessor.service.coldstorage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
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
                                .withPageSize(
                                        4 * 1024 * 1024) // 4MB (대용량 데이터에 최적화)
                                .withRowGroupSize(
                                        128 * 1024 * 1024) // 128MB
                                .build();
                BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {

            // CSV 헤더 스킵
            String header = reader.readLine();
            log.debug("[ParquetExporter] CSV Header: {}", header);

            // CSV 데이터 읽기 및 Parquet 쓰기
            String line;
            long rowCount = 0;

            while ((line = reader.readLine()) != null) {
                GenericRecord record = parseCsvLineToAvroRecord(line, schema);
                writer.write(record);
                rowCount++;

                if (rowCount % 10000 == 0) {
                    log.debug("[ParquetExporter] Processed {} rows", rowCount);
                }
            }

            log.info(
                    "[ParquetExporter] Parquet conversion completed: {} rows",
                    rowCount);

        } catch (Exception e) {
            log.error("[ParquetExporter] Failed to convert CSV to Parquet", e);
            throw new IOException("Parquet conversion failed", e);
        }
    }

    /**
     * GameLog 테이블 스키마 정의 (Avro Schema)
     */
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
     * CSV 라인을 Avro GenericRecord로 파싱
     *
     * @param line CSV 라인
     * @param schema Avro 스키마
     * @return GenericRecord
     */
    private GenericRecord parseCsvLineToAvroRecord(String line, Schema schema) {
        GenericRecord record = new GenericData.Record(schema);

        // CSV 파싱 (간단한 split, 실제로는 CSV 라이브러리 사용 권장)
        List<String> fields = parseCsvLine(line);

        if (fields.size() >= 16) {
            record.put("log_id", fields.get(0));
            record.put("project_id", fields.get(1));
            record.put("session_id", fields.get(2));
            record.put("user_id", fields.get(3).isEmpty() ? null : fields.get(3));
            record.put("severity", fields.get(4));
            record.put("event_category", fields.get(5));
            record.put("archive", fields.get(6));
            record.put("occurred_at", fields.get(7));
            record.put("ingested_at", fields.get(8));
            record.put("trace_id", fields.get(9).isEmpty() ? null : fields.get(9));
            record.put("span_id", fields.get(10).isEmpty() ? null : fields.get(10));
            record.put("fingerprint", fields.get(11));
            record.put("resource", fields.get(12));
            record.put("attributes", fields.get(13));
            record.put("created_at", fields.get(14));
            record.put("updated_at", fields.get(15));
        }

        return record;
    }

    /**
     * CSV 라인 파싱 (RFC 4180 준수)
     *
     * <p>따옴표로 묶인 필드 내의 쉼표와 개행 문자 처리
     *
     * @param line CSV 라인
     * @return 필드 리스트
     */
    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // 따옴표 토글
                insideQuotes = !insideQuotes;
            } else if (c == ',' && !insideQuotes) {
                // 필드 구분자
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }

        // 마지막 필드 추가
        fields.add(currentField.toString());

        return fields;
    }
}

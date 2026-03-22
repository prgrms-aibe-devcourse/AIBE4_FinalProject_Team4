package kr.java.documind.domain.logcollector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.logcollector.model.dto.LogBatchRequest;
import kr.java.documind.domain.logcollector.model.dto.LogEvent;
import kr.java.documind.global.exception.InternalServerException;
import kr.java.documind.global.exception.ServiceUnavailableException;
import kr.java.documind.global.util.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogIngestionService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${redis.stream.key}")
    private String streamKey;

    public void ingestLogBatchToStream(UUID projectId, LogBatchRequest request) {
        // 1. 서버 수집 시간(ingested_at) 일괄 생성
        OffsetDateTime ingestedAt = OffsetDateTime.now(ZoneOffset.UTC);

        // 2. DTO -> LogEvent(Entity/Message) 변환 및 JSON 직렬화
        List<StringRecord> records =
                request.logs().stream()
                        .map(
                                rawLog -> {
                                    LogEvent logEvent =
                                            new LogEvent(
                                                    UuidGenerator.generateV7(), // 시간순 정렬이 가능한 UUID
                                                    // v7[cite: 2]
                                                    projectId,
                                                    ingestedAt,
                                                    rawLog.sessionId(),
                                                    rawLog.userId(),
                                                    rawLog.severity(),
                                                    rawLog.eventCategory(),
                                                    rawLog.occurredAt(),
                                                    rawLog.traceId(),
                                                    rawLog.spanId(),
                                                    rawLog.archive(),
                                                    rawLog.resource(),
                                                    rawLog.attributes());

                                    try {
                                        String payloadJson =
                                                objectMapper.writeValueAsString(logEvent);
                                        return StreamRecords.newRecord()
                                                .ofStrings(java.util.Map.of("payload", payloadJson))
                                                .withStreamKey(streamKey);
                                    } catch (JsonProcessingException e) {
                                        log.error(
                                                "[JSON_PARSE_ERROR] 로그 직렬화 실패. EventCategory: {}",
                                                rawLog.eventCategory(),
                                                e);
                                        // 프로덕션에서는 1건 실패로 전체 배치를 드랍시키지 않고, 실패 건만 DLQ(Dead Letter
                                        // Queue)로 보내는 전략이 좋습니다.
                                        throw new InternalServerException("로그 직렬화 실패", e);
                                    }
                                })
                        .toList();

        // 3. Redis Pipelining을 이용한 Bulk Insert (네트워크 I/O 최적화)
        try {
            redisTemplate.executePipelined(
                    new SessionCallback<Object>() {
                        @Override
                        public <K, V> Object execute(RedisOperations<K, V> operations)
                                throws DataAccessException {
                            StringRedisTemplate stringTemplate = (StringRedisTemplate) operations;
                            for (StringRecord record : records) {
                                stringTemplate.opsForStream().add(record); // 내부적으로 버퍼에 모아 한 번에 전송됨
                            }
                            return null; // 파이프라인 콜백은 null을 반환해야 함
                        }
                    });
            log.debug("{}건의 로그 수집 성공. ProjectID: {}", records.size(), projectId);

        } catch (DataAccessException e) {
            log.error("[CRITICAL] 다건 로그 수집 중 Redis 연결 실패. ProjectID: {}", projectId, e);
            throw new ServiceUnavailableException("일시적인 서비스 장애가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }
}

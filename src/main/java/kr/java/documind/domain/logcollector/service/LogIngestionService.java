package kr.java.documind.domain.logcollector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import kr.java.documind.domain.logcollector.model.dto.LogEvent;
import kr.java.documind.domain.logcollector.model.dto.RawLogRequest;
import kr.java.documind.global.exception.InternalServerException;
import kr.java.documind.global.exception.ServiceUnavailableException;
import kr.java.documind.global.util.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
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

    public void ingestLogToStream(UUID projectId, RawLogRequest request) {
        LogEvent logEvent =
                new LogEvent(
                        UuidGenerator.generateV7(),
                        projectId,
                        OffsetDateTime.now(ZoneOffset.UTC), // ingestedAt. 서버 수신 시간 기록
                        request.sessionId(),
                        request.userId(),
                        request.severity(),
                        request.eventCategory(),
                        request.occurredAt(),
                        request.traceId(),
                        request.spanId(),
                        request.archive(),
                        request.resource(),
                        request.attributes());

        try {
            String payloadJson = objectMapper.writeValueAsString(logEvent);

            MapRecord<String, String, String> record =
                    StreamRecords.newRecord()
                            .ofStrings(java.util.Map.of("payload", payloadJson))
                            .withStreamKey(streamKey);

            redisTemplate.opsForStream().add(record);

            log.debug("로그 수집 성공. LogID: {}, ProjectID: {}", logEvent.logId(), projectId);
        } catch (DataAccessException e) {
            log.error("[CRITICAL] 로그 수집 중 Redis 연결 실패. ProjectID: {}", projectId, e);
            throw new ServiceUnavailableException("일시적인 서비스 장애가 발생했습니다. 잠시 후 다시 시도해주세요.");
        } catch (JsonProcessingException e) {
            log.error("LogEvent를 JSON 문자열로 직렬화하는 데 실패했습니다.", e);
            throw new InternalServerException("로그 직렬화 실패", e);
        }
    }
}

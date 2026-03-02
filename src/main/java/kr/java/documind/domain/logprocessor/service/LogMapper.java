package kr.java.documind.domain.logprocessor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.logprocessor.model.dto.request.RawLogRequest;
import kr.java.documind.domain.logprocessor.model.entity.Log;
import kr.java.documind.domain.logprocessor.model.enums.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogMapper {

    private final ObjectMapper objectMapper;

    public Log toEntity(Map<String, String> map) throws JsonProcessingException {
        if (map.get("projectId") == null || map.get("body") == null) {
            throw new IllegalArgumentException(
                    "Missing required fields: projectId and body are mandatory");
        }

        // logId 처리: null이거나 빈 문자열이면 새 UUID 생성
        String logIdStr = map.get("logId");
        UUID logId =
                (logIdStr != null && !logIdStr.isEmpty())
                        ? UUID.fromString(logIdStr)
                        : UUID.randomUUID();

        // sessionId 처리: NOT NULL 제약 때문에 null이면 기본값 제공
        String sessionId = map.get("sessionId");
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "unknown-session";
            log.warn("sessionId is null or empty. Using default value: 'unknown-session'");
        }

        return Log.builder()
                .logId(logId)
                .projectId(map.get("projectId"))
                .sessionId(sessionId)
                .userId(map.get("userId"))
                .severity(LogLevel.fromString(map.getOrDefault("severity", "INFO")))
                .body(map.get("body"))
                .occurredAt(parseTime(map.get("occurredAt")))
                .ingestedAt(parseTime(map.get("ingestedAt")))
                .traceId(map.get("traceId"))
                .spanId(map.get("spanId"))
                .fingerprint(map.get("fingerprint"))
                .resource(
                        objectMapper.readValue(
                                map.getOrDefault("resource", "{}"),
                                new TypeReference<Map<String, Object>>() {}))
                .attributes(
                        objectMapper.readValue(
                                map.getOrDefault("attributes", "{}"),
                                new TypeReference<Map<String, Object>>() {}))
                .build();
    }

    public Log toEntity(RawLogRequest dto) {
        return Log.builder()
                .logId(UUID.randomUUID())
                .projectId(dto.projectId())
                .sessionId(dto.sessionId())
                .userId(dto.userId())
                .severity(dto.severity())
                .body(dto.body())
                .occurredAt(parseTime(dto.occurredAt()))
                .ingestedAt(OffsetDateTime.now())
                .traceId(dto.traceId())
                .spanId(dto.spanId())
                .fingerprint(null)
                .resource(dto.resource() != null ? dto.resource() : Map.of())
                .attributes(dto.attributes() != null ? dto.attributes() : Map.of())
                .build();
    }

    private OffsetDateTime parseTime(String timeStr) {
        if (timeStr == null) return OffsetDateTime.now();
        try {
            return OffsetDateTime.parse(timeStr);
        } catch (Exception e) {
            log.warn(
                    "Failed to parse timestamp '{}'. Falling back to current time. Error: {}",
                    timeStr,
                    e.getMessage());
            return OffsetDateTime.now();
        }
    }
}

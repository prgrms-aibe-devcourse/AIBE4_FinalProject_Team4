package kr.java.documind.domain.logprocessor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.issue.service.fingerprint.FingerprintGenerator;
import kr.java.documind.domain.issue.service.fingerprint.FingerprintResult;
import kr.java.documind.domain.logprocessor.model.dto.LogWithFingerprint;
import kr.java.documind.domain.logprocessor.model.dto.request.RawLogRequest;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.enums.EventCategory;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogMapper {

    private final ObjectMapper objectMapper;
    private final FingerprintGenerator fingerprintGenerator;

    public GameLog toEntity(Map<String, String> map) throws JsonProcessingException {
        if (map.get("projectId") == null || map.get("archive") == null) {
            throw new IllegalArgumentException(
                    "Missing required fields: projectId and archive are mandatory");
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

        OffsetDateTime now = OffsetDateTime.now();

        // fingerprint 처리: null이면 생성
        String fingerprint = map.get("fingerprint");
        if (fingerprint == null || fingerprint.isEmpty()) {
            // 임시 엔티티로 fingerprint 생성
            GameLog tempLog =
                    GameLog.builder()
                            .archive(map.get("archive"))
                            .severity(
                                    LogSeverity.fromString(map.getOrDefault("severity", "INFO")))
                            .build();
            fingerprint = fingerprintGenerator.generate(tempLog).getFingerprint();
            log.debug("Generated fingerprint for Redis Stream message: {}", fingerprint);
        }

        return GameLog.builder()
                .logId(logId)
                .projectId(UUID.fromString(map.get("projectId")))
                .sessionId(sessionId)
                .userId(map.get("userId"))
                .severity(LogSeverity.fromString(map.getOrDefault("severity", "INFO")))
                .eventCategory(
                        EventCategory.fromString(map.getOrDefault("eventCategory", "SYSTEM")))
                .archive(map.get("archive"))
                .occurredAt(parseTime(map.get("occurredAt")))
                .ingestedAt(parseTime(map.get("ingestedAt")))
                .traceId(map.get("traceId"))
                .spanId(map.get("spanId"))
                .fingerprint(fingerprint)
                .resource(
                        objectMapper.readValue(
                                map.getOrDefault("resource", "{}"),
                                new TypeReference<Map<String, Object>>() {}))
                .attributes(
                        objectMapper.readValue(
                                map.getOrDefault("attributes", "{}"),
                                new TypeReference<Map<String, Object>>() {}))
                .createdAt(parseTime(map.get("createdAt"), now))
                .updatedAt(parseTime(map.get("updatedAt"), now))
                .build();
    }

    public GameLog toEntity(RawLogRequest dto) {
        return toEntityWithFingerprint(dto).log();
    }

    /**
     * RawLogRequest를 GameLog로 변환하고 FingerprintResult도 함께 반환
     *
     * <p>이슈 그룹핑 시 fingerprint quality 정보가 필요하므로 함께 반환
     *
     * @param dto RawLogRequest
     * @return GameLog와 FingerprintResult
     */
    public LogWithFingerprint toEntityWithFingerprint(RawLogRequest dto) {
        OffsetDateTime now = OffsetDateTime.now();

        // 임시 엔티티 생성 (fingerprint 생성을 위해 archive 필요)
        GameLog tempLog =
                GameLog.builder()
                        .logId(UUID.randomUUID())
                        .projectId(dto.projectId())
                        .sessionId(dto.sessionId())
                        .userId(dto.userId())
                        .severity(dto.severity())
                        .eventCategory(dto.eventCategory())
                        .archive(dto.archive())
                        .occurredAt(parseTime(dto.occurredAt(), now))
                        .ingestedAt(now)
                        .traceId(dto.traceId())
                        .spanId(dto.spanId())
                        .fingerprint(null) // 임시값
                        .resource(dto.resource() != null ? dto.resource() : Map.of())
                        .attributes(dto.attributes() != null ? dto.attributes() : Map.of())
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        // fingerprint 생성
        FingerprintResult fingerprintResult = fingerprintGenerator.generate(tempLog);

        // 최종 엔티티 생성 (fingerprint 포함)
        GameLog finalLog =
                GameLog.builder()
                        .logId(tempLog.getLogId())
                        .projectId(tempLog.getProjectId())
                        .sessionId(tempLog.getSessionId())
                        .userId(tempLog.getUserId())
                        .severity(tempLog.getSeverity())
                        .eventCategory(tempLog.getEventCategory())
                        .archive(tempLog.getArchive())
                        .occurredAt(tempLog.getOccurredAt())
                        .ingestedAt(tempLog.getIngestedAt())
                        .traceId(tempLog.getTraceId())
                        .spanId(tempLog.getSpanId())
                        .fingerprint(fingerprintResult.getFingerprint())
                        .resource(tempLog.getResource())
                        .attributes(tempLog.getAttributes())
                        .createdAt(tempLog.getCreatedAt())
                        .updatedAt(tempLog.getUpdatedAt())
                        .build();

        return new LogWithFingerprint(finalLog, fingerprintResult);
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

    private OffsetDateTime parseTime(String timeStr, OffsetDateTime defaultTime) {
        if (timeStr == null) return defaultTime;
        try {
            return OffsetDateTime.parse(timeStr);
        } catch (Exception e) {
            log.warn(
                    "Failed to parse timestamp '{}'. Falling back to default time. Error: {}",
                    timeStr,
                    e.getMessage());
            return defaultTime;
        }
    }
}

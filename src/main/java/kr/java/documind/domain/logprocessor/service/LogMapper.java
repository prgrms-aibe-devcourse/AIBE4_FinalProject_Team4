package kr.java.documind.domain.logprocessor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.issue.service.fingerprint.FingerprintGenerator;
import kr.java.documind.domain.issue.service.fingerprint.FingerprintResult;
import kr.java.documind.domain.logcollector.model.dto.LogEvent;
import kr.java.documind.domain.logprocessor.model.dto.LogWithFingerprint;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.enums.EventCategory;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import kr.java.documind.global.exception.InternalServerException;
import kr.java.documind.global.util.UuidGenerator;
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
        String payloadJson = map.get("payload");
        if (payloadJson == null) {
            throw new InternalServerException("시스템 오류: Redis Stream 메시지에 필수 데이터(payload)가 누락되었습니다.");
        }

        LogEvent flatLog = objectMapper.readValue(payloadJson, LogEvent.class);
        return toEntity(flatLog);
    }

    public GameLog toEntity(LogEvent dto) {
        return toEntityWithFingerprint(dto).log();
    }

    public LogWithFingerprint toEntityWithFingerprint(LogEvent dto) {
        if (dto.projectId() == null) {
            throw new InternalServerException("시스템 오류: 로그 이벤트에 프로젝트 식별자(projectId)가 누락되어 처리를 중단합니다.");
        }

        UUID logId = dto.logId() != null ? dto.logId() : UuidGenerator.generateV7();

        String sessionId = dto.sessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "unknown-session";
            log.warn("sessionId가 null이거나 비어있습니다. 기본값('unknown-session')을 사용합니다.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime ingestedAt = dto.ingestedAt() != null ? dto.ingestedAt() : now;
        OffsetDateTime occurredAt = parseTime(dto.occurredAt(), ingestedAt);

        // fingerprint 생성을 위한 임시 엔티티 생성
        GameLog tempLog =
                GameLog.builder()
                        .logId(logId)
                        .projectId(dto.projectId())
                        .sessionId(sessionId)
                        .userId(dto.userId())
                        .severity(
                                LogSeverity.fromString(
                                        dto.severity() != null ? dto.severity() : "UNKNOWN"))
                        .eventCategory(
                                EventCategory.fromString(
                                        dto.eventCategory() != null
                                                ? dto.eventCategory()
                                                : "UNKNOWN"))
                        .archive(dto.archive())
                        .occurredAt(occurredAt)
                        .ingestedAt(ingestedAt)
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

    /**
     * 시간 문자열 파싱 및 유효성 검증(보정) 수행
     * @param timeStr 파싱할 클라이언트 전송 시간 문자열
     * @param fallbackTime 파싱 실패 또는 유효 범위 초과 시 대체할 시간 (주로 ingestedAt)
     */
    private OffsetDateTime parseTime(String timeStr, OffsetDateTime fallbackTime) {
        OffsetDateTime parsedTime;

        if (timeStr == null) {
            parsedTime = fallbackTime;
        } else {
            try {
                parsedTime = OffsetDateTime.parse(timeStr);
            } catch (Exception e) {
                log.warn("타임스탬프 '{}' 파싱에 실패했습니다. 기본 시간으로 대체합니다. 에러: {}", timeStr, e.getMessage());
                parsedTime = fallbackTime;
            }
        }

        // 유효하지 않은 과거/미래 시간인 경우 fallbackTime으로 대체
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (parsedTime.isAfter(now.plusHours(1)) || parsedTime.isBefore(now.minusDays(30))) {
            log.warn("유효하지 않은 발생 시간(occurred_at)이 감지되었습니다: {}. 대체 시간으로 조정합니다: {}", parsedTime, fallbackTime);
            return fallbackTime;
        }

        return parsedTime;
    }
}

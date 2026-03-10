package kr.java.documind.domain.logcollector.model.dto;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/** 게임 클라이언트 원본 데이터와 서버 생성 메타데이터가 통합된 단일 DTO */
public record LogEvent(
        // 서버가 보장하는 메타데이터
        UUID logId,
        UUID projectId,
        OffsetDateTime ingestedAt,

        // 클라이언트 원본 데이터
        String sessionId,
        String userId,
        String severity,
        String eventCategory,
        String occurredAt,
        String traceId,
        String spanId,
        String archive,
        Map<String, Object> resource,
        Map<String, Object> attributes) {

    /** 파이프라인 처리 중 NullPointerException이 터지지 않도록 방어 로직 수행 */
    public LogEvent {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "unknown-session";
        }
        if (severity == null || severity.isBlank()) {
            severity = "UNKNOWN";
        }
        if (eventCategory == null || eventCategory.isBlank()) {
            eventCategory = "UNKNOWN";
        }
        if (archive == null) {
            archive = "";
        }
        if (occurredAt == null || occurredAt.isBlank()) {
            occurredAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (resource == null) {
            resource = Map.of();
        }
        if (attributes == null) {
            attributes = Map.of();
        }
    }
}

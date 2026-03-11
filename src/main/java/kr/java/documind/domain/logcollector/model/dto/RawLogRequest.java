package kr.java.documind.domain.logcollector.model.dto;

import java.util.Map;

/** 게임 클라이언트/서버로부터 수신하는 OTel 규격 기반의 원본 로그 데이터 DTO */
public record RawLogRequest(
        String sessionId,
        String userId,
        String severity,
        String eventCategory,
        String occurredAt,
        String traceId,
        String spanId,
        String archive, // 📝 본문 저장용 TEXT (String)
        Map<String, Object> resource,
        Map<String, Object> attributes) {}

package kr.java.documind.domain.logprocessor.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import kr.java.documind.domain.logprocessor.model.enums.LogLevel;

public record RawLogRequest(
        @NotBlank(message = "Project ID cannot be blank") String projectId, // 프로젝트 식별자
        @NotBlank(message = "Session ID cannot be blank") String sessionId, // 게임 세션 ID
        String userId, // 유저 식별자 (Nullable)
        @NotNull(message = "Severity cannot be null") LogLevel severity, // 로그 레벨
        @NotBlank(message = "Log body cannot be blank") String body, // 로그 본문
        @NotBlank(message = "Occurred-at timestamp cannot be blank")
                String occurredAt, // 클라이언트 발생 시각 (String으로 수신 후 보정)
        String traceId, // 트랜잭션 추적 ID
        String spanId, // 구간 추적 ID
        Map<String, Object> resource, // 정적 환경 정보 (Semantic Convention)
        Map<String, Object> attributes // 동적 상황 정보 (Performance, Context 등)
        ) {}

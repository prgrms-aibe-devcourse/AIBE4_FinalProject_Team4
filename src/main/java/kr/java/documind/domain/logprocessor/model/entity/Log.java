package kr.java.documind.domain.logprocessor.model.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.logprocessor.model.enums.LogLevel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity(name = "log")
@Table(name = "log", indexes = @Index(name = "idx_occurred_at", columnList = "occurred_at"))
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Log {

    @Id private UUID logId;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String sessionId;

    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogLevel severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, updatable = false) // 수정 방지
    private OffsetDateTime occurredAt; // 파티션 키

    @Column(nullable = false, updatable = false) // 수정 방지
    private OffsetDateTime ingestedAt; // 서버 수집 시각

    private String traceId;
    private String spanId;
    private String fingerprint; // 이슈 그룹핑 해시

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> resource;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> attributes;

    // Todo 비즈니스 로직
}

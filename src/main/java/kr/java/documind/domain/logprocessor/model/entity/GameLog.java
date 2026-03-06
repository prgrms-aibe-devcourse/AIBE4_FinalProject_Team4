package kr.java.documind.domain.logprocessor.model.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.logprocessor.model.enums.EventCategory;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity(name = "game_log")
@Table(name = "game_log")
@IdClass(GameLogId.class)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameLog {

    @Id private UUID logId;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String sessionId;

    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "event_category")
    private EventCategory eventCategory;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String archive;

    @Id
    @Column(nullable = false, updatable = false) // 수정 방지
    private OffsetDateTime occurredAt; // 파티션 키 (복합 PK)

    @Column(nullable = false, updatable = false) // 수정 방지
    private OffsetDateTime ingestedAt; // 서버 수집 시각

    private String traceId;
    private String spanId;

    @Column(nullable = false, length = 64)
    private String fingerprint; // 이슈 그룹핑 해시

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> resource;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> attributes;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt; // 생성 시각

    @Column(nullable = false)
    private OffsetDateTime updatedAt; // 수정 시각

    // Todo 비즈니스 로직
}

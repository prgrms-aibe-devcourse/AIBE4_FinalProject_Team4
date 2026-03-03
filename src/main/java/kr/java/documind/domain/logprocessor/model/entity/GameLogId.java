package kr.java.documind.domain.logprocessor.model.entity;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * GameLog 엔티티의 복합 기본 키 클래스
 *
 * <p>Range Partitioning을 위해 파티션 키(occurred_at)가 PRIMARY KEY에 포함되어야 함
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GameLogId implements Serializable {

    private UUID logId;
    private OffsetDateTime occurredAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameLogId that)) return false;
        return Objects.equals(logId, that.logId)
                && Objects.equals(occurredAt, that.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logId, occurredAt);
    }
}

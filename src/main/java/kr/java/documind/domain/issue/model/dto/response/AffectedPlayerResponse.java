package kr.java.documind.domain.issue.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 영향받은 플레이어 정보
 *
 * <p>특정 이슈로 인해 영향을 받은 플레이어의 통계 정보
 */
@Schema(description = "영향받은 플레이어 정보")
public record AffectedPlayerResponse(
        @Schema(description = "플레이어 ID", example = "player_12345") String userId,
        @Schema(description = "발생 횟수", example = "15") Long occurrenceCount,
        @Schema(description = "최초 발생 시각", example = "2024-03-11T10:00:00Z")
                OffsetDateTime firstOccurredAt,
        @Schema(description = "최근 발생 시각", example = "2024-03-11T15:30:00Z")
                OffsetDateTime lastOccurredAt) {}

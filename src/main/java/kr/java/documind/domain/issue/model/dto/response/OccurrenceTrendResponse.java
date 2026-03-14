package kr.java.documind.domain.issue.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 이슈 발생 추이 응답
 *
 * <p>시간대별 이슈 발생 횟수 통계
 */
@Schema(description = "이슈 발생 추이 응답")
public record OccurrenceTrendResponse(
        @Schema(description = "날짜", example = "2026-03-13") LocalDate date,
        @Schema(description = "발생 횟수", example = "15") Long count) {}

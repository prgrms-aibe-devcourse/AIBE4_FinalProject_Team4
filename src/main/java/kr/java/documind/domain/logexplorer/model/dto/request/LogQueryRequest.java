package kr.java.documind.domain.logexplorer.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 로그 탐색기 조회 요청.
 *
 * @param from 조회 시작 시각
 * @param to 조회 종료 시각
 * @param timeField 시간 기준 컬럼. "occurred_at" 또는 "ingested_at".
 * @param selects SELECT 항목 목록. 빈 경우 모든 컬럼 조회.
 * @param wheres WHERE 필터 목록.
 * @param whereLogic 필터 간 논리 연산. "AND" 또는 "OR".
 * @param groupBy GROUP BY 컬럼 목록.
 * @param orderBy 정렬 조건.
 * @param limit 최대 조회 건수 (1~2000).
 * @param offset 페이징 오프셋 (기본 0).
 */
public record LogQueryRequest(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @NotNull String timeField,
        @Size(max = 10) @Valid List<SelectField> selects,
        @Size(max = 10) @Valid List<WhereFilter> wheres,
        @NotNull String whereLogic,
        @Size(max = 3) List<String> groupBy,
        OrderByClause orderBy,
        @Min(1) @Max(2000) int limit,
        @Min(0) long offset) {}

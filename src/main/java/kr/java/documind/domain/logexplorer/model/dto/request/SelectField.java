package kr.java.documind.domain.logexplorer.model.dto.request;

import kr.java.documind.domain.logexplorer.model.enums.AggregationFunction;

/**
 * SELECT 절 하나의 항목.
 *
 * @param column 컬럼명 (예: "severity", "attributes.level"). null이면 COUNT(*) 용도로 사용.
 * @param aggregation 집계 함수. null이면 단순 컬럼 조회.
 * @param alias 출력 alias. null 가능.
 */
public record SelectField(String column, AggregationFunction aggregation, String alias) {}

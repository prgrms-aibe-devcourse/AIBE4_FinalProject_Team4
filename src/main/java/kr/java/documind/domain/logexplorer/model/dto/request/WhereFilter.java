package kr.java.documind.domain.logexplorer.model.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import kr.java.documind.domain.logexplorer.model.enums.FilterOperator;

/**
 * WHERE 절 하나의 필터 조건.
 *
 * @param column 컬럼명 (예: "severity", "attributes.level"). 화이트리스트 검증 필수.
 * @param operator 비교 연산자.
 * @param value 단일 값 (EQ/NEQ/GT 등).
 * @param value2 두 번째 값 (BETWEEN/NOT_BETWEEN 전용).
 * @param values 다중 값 리스트 (ANY_IN/NOT_IN 전용).
 */
public record WhereFilter(
        @NotNull String column,
        @NotNull FilterOperator operator,
        String value,
        String value2,
        List<String> values) {}

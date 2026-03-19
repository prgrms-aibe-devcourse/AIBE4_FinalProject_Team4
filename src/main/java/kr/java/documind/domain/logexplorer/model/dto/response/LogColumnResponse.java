package kr.java.documind.domain.logexplorer.model.dto.response;

import java.util.List;
import java.util.Map;

/**
 * 조회 가능한 컬럼 메타데이터.
 *
 * @param columns 정적 컬럼 목록 (QueryableColumn enum 기반).
 * @param jsonbKeys JSONB 컬럼별 동적 키 목록. key = 컬럼명(예: "attributes"), value = 키 목록.
 */
public record LogColumnResponse(List<ColumnMeta> columns, Map<String, List<String>> jsonbKeys) {

    /**
     * 단일 컬럼 메타데이터.
     *
     * @param name DB 컬럼명.
     * @param dataType 데이터 타입 ("string", "datetime", "jsonb").
     * @param isJsonb JSONB 컬럼 여부.
     */
    public record ColumnMeta(String name, String dataType, boolean isJsonb) {}
}

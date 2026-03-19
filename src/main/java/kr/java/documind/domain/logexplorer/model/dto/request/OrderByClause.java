package kr.java.documind.domain.logexplorer.model.dto.request;

/**
 * ORDER BY 절.
 *
 * @param column 정렬 컬럼명. null이면 기본 정렬(occurred_at DESC) 적용.
 * @param direction "ASC" 또는 "DESC". 그 외 값은 "ASC"로 대체.
 */
public record OrderByClause(String column, String direction) {}

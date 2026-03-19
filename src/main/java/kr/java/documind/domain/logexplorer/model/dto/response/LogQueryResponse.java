package kr.java.documind.domain.logexplorer.model.dto.response;

import java.util.List;
import java.util.Map;

/**
 * 로그 탐색기 조회 결과.
 *
 * @param rows 결과 행 목록. 각 행은 컬럼명 → 값 맵.
 * @param columnNames 결과 컬럼명 목록 (테이블 헤더 렌더링 용도).
 * @param hasMore 다음 페이지 존재 여부.
 */
public record LogQueryResponse(
        List<Map<String, Object>> rows, List<String> columnNames, boolean hasMore) {}

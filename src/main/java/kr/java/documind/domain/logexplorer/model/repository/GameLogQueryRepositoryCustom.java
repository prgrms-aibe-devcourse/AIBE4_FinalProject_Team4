package kr.java.documind.domain.logexplorer.model.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.logexplorer.model.dto.request.LogQueryRequest;
import kr.java.documind.domain.logexplorer.model.dto.response.LogQueryResponse;

/** 로그 탐색기용 동적 쿼리 레포지토리. */
public interface GameLogQueryRepositoryCustom {

    /**
     * 동적 조건으로 game_log 조회.
     *
     * @param projectId 프로젝트 UUID
     * @param request 쿼리 요청
     * @return 조회 결과
     */
    LogQueryResponse executeQuery(UUID projectId, LogQueryRequest request);

    /**
     * JSONB 컬럼의 상위 키 탐색 (최근 200건 기준).
     *
     * @param projectId 프로젝트 UUID
     * @param jsonbColumn "attributes" 또는 "resource"
     * @return 발견된 키 목록
     */
    List<String> discoverJsonbKeys(UUID projectId, String jsonbColumn);

    /**
     * JSONB 컬럼별 동적 키 목록 일괄 탐색.
     *
     * @param projectId 프로젝트 UUID
     * @return 컬럼명 → 키 목록
     */
    Map<String, List<String>> discoverAllJsonbKeys(UUID projectId);
}

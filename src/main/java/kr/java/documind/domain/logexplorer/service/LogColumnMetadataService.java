package kr.java.documind.domain.logexplorer.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.logexplorer.model.dto.response.LogColumnResponse;
import kr.java.documind.domain.logexplorer.model.dto.response.LogColumnResponse.ColumnMeta;
import kr.java.documind.domain.logexplorer.model.enums.QueryableColumn;
import kr.java.documind.domain.logexplorer.model.repository.GameLogQueryRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** 조회 가능한 컬럼 메타데이터 제공 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogColumnMetadataService {

    private final GameLogQueryRepositoryCustom gameLogQueryRepository;

    /**
     * 프로젝트의 컬럼 메타데이터를 반환한다.
     *
     * <p>정적 컬럼({@link QueryableColumn})과 최근 200건에서 탐색한 동적 JSONB 키를 합쳐서 반환.
     *
     * @param projectId 프로젝트 UUID
     * @return 컬럼 메타데이터
     */
    @Cacheable(value = "logColumns", key = "#projectId")
    public LogColumnResponse getColumnMetadata(UUID projectId) {
        List<ColumnMeta> columns =
                Arrays.stream(QueryableColumn.values())
                        .map(
                                col ->
                                        new ColumnMeta(
                                                col.getDbName(), col.getDataType(), col.isJsonb()))
                        .toList();

        Map<String, List<String>> jsonbKeys =
                gameLogQueryRepository.discoverAllJsonbKeys(projectId);

        log.debug(
                "[LogColumnMetadata] projectId={} columns={} jsonbKeys={}",
                projectId,
                columns.size(),
                jsonbKeys);

        return new LogColumnResponse(columns, jsonbKeys);
    }
}

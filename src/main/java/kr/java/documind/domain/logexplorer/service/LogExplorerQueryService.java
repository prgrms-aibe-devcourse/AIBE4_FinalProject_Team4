package kr.java.documind.domain.logexplorer.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.logexplorer.model.dto.request.LogQueryRequest;
import kr.java.documind.domain.logexplorer.model.dto.request.SelectField;
import kr.java.documind.domain.logexplorer.model.dto.request.WhereFilter;
import kr.java.documind.domain.logexplorer.model.dto.response.LogQueryResponse;
import kr.java.documind.domain.logexplorer.model.enums.QueryableColumn;
import kr.java.documind.domain.logexplorer.model.repository.GameLogQueryRepositoryCustom;
import kr.java.documind.global.exception.InvalidQueryException;
import kr.java.documind.global.exception.QueryComplexityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 로그 탐색기 쿼리 실행 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogExplorerQueryService {

    private static final long MAX_RANGE_DAYS = 90;

    private final GameLogQueryRepositoryCustom gameLogQueryRepository;

    /**
     * 로그 탐색 쿼리를 실행한다.
     *
     * @param projectId 프로젝트 UUID
     * @param request 쿼리 요청
     * @return 조회 결과
     */
    public LogQueryResponse query(UUID projectId, LogQueryRequest request) {
        validateComplexity(request);
        validateRequest(request);
        return gameLogQueryRepository.executeQuery(projectId, request);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Validation
    // ──────────────────────────────────────────────────────────────────────────

    private void validateComplexity(LogQueryRequest request) {
        if (request.from() == null || request.to() == null) {
            return;
        }
        if (request.from().isAfter(request.to())) {
            throw new InvalidQueryException("from은 to보다 이전 시각이어야 합니다.");
        }
        Duration range = Duration.between(request.from(), request.to());
        if (range.toDays() > MAX_RANGE_DAYS) {
            throw new QueryComplexityException(
                    "조회 시간 범위는 최대 " + MAX_RANGE_DAYS + "일입니다. 요청된 범위: " + range.toDays() + "일");
        }
    }

    private void validateRequest(LogQueryRequest request) {
        validateSelectFields(request.selects());
        validateWhereFilters(request.wheres());
        validateGroupBy(request.groupBy(), request.selects());
    }

    private void validateSelectFields(List<SelectField> selects) {
        if (selects == null || selects.isEmpty()) {
            return;
        }
        for (SelectField field : selects) {
            if (field.column() != null && !field.column().isBlank()) {
                QueryableColumn.parseColumn(field.column()); // 화이트리스트 검증
            } else if (field.aggregation() == null) {
                throw new InvalidQueryException("SELECT 항목에 컬럼 또는 집계 함수가 필요합니다.");
            }
        }
    }

    private void validateWhereFilters(List<WhereFilter> wheres) {
        if (wheres == null || wheres.isEmpty()) {
            return;
        }
        for (WhereFilter filter : wheres) {
            if (filter.column() == null || filter.column().isBlank()) {
                throw new InvalidQueryException("WHERE 조건에 컬럼명이 필요합니다.");
            }
            QueryableColumn.parseColumn(filter.column()); // 화이트리스트 검증

            if (filter.operator() == null) {
                throw new InvalidQueryException("WHERE 조건에 연산자가 필요합니다.");
            }
            if (filter.operator().requiresOneValue()
                    && (filter.value() == null || filter.value().isBlank())) {
                throw new InvalidQueryException(filter.operator().name() + " 연산자는 값이 필요합니다.");
            }
            if (filter.operator().requiresTwoValues()
                    && (filter.value() == null
                            || filter.value2() == null
                            || filter.value().isBlank()
                            || filter.value2().isBlank())) {
                throw new InvalidQueryException(filter.operator().name() + " 연산자는 두 개의 값이 필요합니다.");
            }
        }
    }

    private void validateGroupBy(List<String> groupBy, List<SelectField> selects) {
        if (groupBy == null || groupBy.isEmpty()) {
            return;
        }
        // GROUP BY 사용 시 SELECT에 집계 함수가 있어야 유의미
        if (selects == null || selects.isEmpty()) {
            throw new InvalidQueryException("GROUP BY를 사용할 때는 SELECT 항목(집계 함수 포함)을 명시해야 합니다.");
        }
        boolean hasAggregation = selects.stream().anyMatch(s -> s.aggregation() != null);
        if (!hasAggregation) {
            throw new InvalidQueryException("GROUP BY를 사용할 때는 최소 하나의 집계 함수가 SELECT에 포함되어야 합니다.");
        }
        for (String col : groupBy) {
            QueryableColumn.parseColumn(col); // 화이트리스트 검증
        }
    }
}

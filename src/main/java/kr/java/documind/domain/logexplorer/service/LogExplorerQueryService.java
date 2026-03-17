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

    private void validateColumnRef(String columnName) {
        QueryableColumn col = QueryableColumn.parseColumn(columnName);
        if (col.isJsonb() && !columnName.contains(".")) {
            throw new InvalidQueryException(
                    "원본 JSONB 컬럼("
                            + col.name()
                            + ")은 직접 선택하거나 검색할 수 없습니다. 하위 경로를 지정해주세요. (예: "
                            + col.name()
                            + ".key)");
        }
    }

    private void validateSelectFields(List<SelectField> selects) {
        if (selects == null || selects.isEmpty()) {
            return;
        }
        for (SelectField field : selects) {
            if (field.column() != null && !field.column().isBlank()) {
                validateColumnRef(field.column()); // 화이트리스트 및 JSONB 단일 사용 검증
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
            validateColumnRef(filter.column());

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
        boolean hasGroupBy = groupBy != null && !groupBy.isEmpty();
        boolean hasAggregation =
                selects != null && selects.stream().anyMatch(s -> s.aggregation() != null);

        if (hasGroupBy && !hasAggregation) {
            throw new InvalidQueryException("GROUP BY를 사용할 때는 최소 하나의 집계 함수가 SELECT에 포함되어야 합니다.");
        }

        // 집계 함수가 쿼리에 포함되어 있을 때, 비집계 컬럼(일반 컬럼)이 문법에 맞게 쓰였는지 철저히 검사
        if (hasAggregation && selects != null) {
            for (SelectField field : selects) {
                boolean isNonAggregated =
                        field.aggregation() == null
                                && field.column() != null
                                && !field.column().isBlank();

                if (isNonAggregated) {
                    if (hasGroupBy && !groupBy.contains(field.column())) {
                        // 에러 케이스 1: GROUP BY를 썼는데, SELECT 절의 일반 컬럼이 GROUP BY 목록에 누락된 경우
                        throw new InvalidQueryException(
                                "집계 함수와 함께 조회하는 일반 컬럼은 반드시 GROUP BY 절에 명시해야 합니다: "
                                        + field.column());
                    } else if (!hasGroupBy) {
                        // 에러 케이스 2: GROUP BY 없이 전체 집계(COUNT 등)를 하면서 일반 컬럼을 같이 조회하려는 경우
                        throw new InvalidQueryException(
                                "GROUP BY 없이 집계 함수를 사용할 때 일반 컬럼을 함께 조회할 수 없습니다: " + field.column());
                    }
                }
            }
        }

        if (hasGroupBy) {
            for (String col : groupBy) {
                validateColumnRef(col);
            }
        }
    }
}

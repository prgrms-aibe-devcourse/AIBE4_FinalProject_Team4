package kr.java.documind.domain.logexplorer.model.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.java.documind.domain.logexplorer.model.dto.request.LogQueryRequest;
import kr.java.documind.domain.logexplorer.model.dto.request.OrderByClause;
import kr.java.documind.domain.logexplorer.model.dto.request.SelectField;
import kr.java.documind.domain.logexplorer.model.dto.request.WhereFilter;
import kr.java.documind.domain.logexplorer.model.dto.response.LogQueryResponse;
import kr.java.documind.domain.logexplorer.model.enums.AggregationFunction;
import kr.java.documind.domain.logexplorer.model.enums.QueryableColumn;
import kr.java.documind.global.exception.InvalidQueryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC 기반 동적 로그 쿼리 구현체.
 *
 * <p>SQL Injection 방어 원칙:
 *
 * <ul>
 *   <li>컬럼명은 반드시 {@link QueryableColumn} 화이트리스트에서 조회된 값만 사용
 *   <li>JSONB 경로 세그먼트는 {@code ^[a-zA-Z0-9_]{1,64}$} 패턴으로 검증 후 리터럴 삽입
 *   <li>모든 사용자 입력 값은 JDBC {@code ?} 파라미터로 바인딩
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GameLogQueryRepositoryImpl implements GameLogQueryRepositoryCustom {

    private static final Set<String> ALLOWED_JSONB_COLUMNS = Set.of("attributes", "resource");
    private static final Set<String> ALLOWED_TIME_FIELDS = Set.of("occurred_at", "ingested_at");
    private static final Set<String> ALLOWED_DIRECTIONS = Set.of("ASC", "DESC");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public LogQueryResponse executeQuery(UUID projectId, LogQueryRequest request) {
        List<Object> params = new ArrayList<>();

        String selectClause = buildSelectClause(request.selects());
        String whereClause = buildWhereClause(request, projectId, params);
        String groupByClause = buildGroupByClause(request.groupBy());

        // Global Aggregation (GROUP BY 없이 집계 함수만 사용) 시 ORDER BY 불가 — SQL 표준 위반
        boolean isGlobalAggregation = isGlobalAggregation(request.selects(), request.groupBy());
        String orderByClause = isGlobalAggregation ? "" : buildOrderByClause(request.orderBy());

        long safeLimit = Math.min(request.limit(), 2000);
        params.add(safeLimit + 1L);
        params.add(request.offset());

        String sql =
                "SELECT "
                        + selectClause
                        + " FROM game_log "
                        + whereClause
                        + groupByClause
                        + orderByClause
                        + " LIMIT ? OFFSET ?";

        log.debug("[LogExplorer] Executing query: {} | params count: {}", sql, params.size());

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, params.toArray());

        boolean hasMore = results.size() > safeLimit;
        if (hasMore) {
            results = new ArrayList<>(results.subList(0, (int) safeLimit));
        }

        List<String> columnNames =
                results.isEmpty()
                        ? deriveColumnNames(request.selects())
                        : new ArrayList<>(results.get(0).keySet());

        return new LogQueryResponse(results, columnNames, hasMore);
    }

    @Override
    public List<String> discoverJsonbKeys(UUID projectId, String jsonbColumn) {
        if (!ALLOWED_JSONB_COLUMNS.contains(jsonbColumn)) {
            throw new InvalidQueryException("허용되지 않는 JSONB 컬럼: " + jsonbColumn);
        }
        String sql =
                "SELECT DISTINCT jsonb_object_keys("
                        + jsonbColumn
                        + ") "
                        + "FROM (SELECT "
                        + jsonbColumn
                        + " FROM game_log WHERE project_id = ? ORDER BY occurred_at DESC LIMIT 200) r";
        return jdbcTemplate.queryForList(sql, String.class, projectId);
    }

    @Override
    public Map<String, List<String>> discoverAllJsonbKeys(UUID projectId) {
        Map<String, List<String>> result = new HashMap<>();
        for (String col : ALLOWED_JSONB_COLUMNS) {
            result.put(col, discoverJsonbKeys(projectId, col));
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Global Aggregation 판별
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Global Aggregation 여부를 판별한다.
     *
     * <p>SELECT에 집계 함수가 있고 GROUP BY가 없는 경우, ORDER BY를 붙이면 SQL 표준 위반(BadSqlGrammarException)이 발생한다.
     * 이 경우 ORDER BY 절을 생략해야 한다.
     *
     * @param selects SELECT 항목 목록
     * @param groupBy GROUP BY 컬럼 목록
     * @return GROUP BY 없이 집계 함수만 사용하는 경우 true
     */
    private boolean isGlobalAggregation(List<SelectField> selects, List<String> groupBy) {
        if (selects == null || selects.isEmpty()) {
            return false;
        }
        boolean hasGroupBy = groupBy != null && !groupBy.isEmpty();
        if (hasGroupBy) {
            return false;
        }
        return selects.stream().anyMatch(s -> s.aggregation() != null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SELECT 절 빌더
    // ──────────────────────────────────────────────────────────────────────────

    private String buildSelectClause(List<SelectField> selects) {
        if (selects == null || selects.isEmpty()) {
            return "log_id, project_id, session_id, user_id, severity, event_category, archive,"
                    + " occurred_at, ingested_at, trace_id, span_id, fingerprint";
        }
        return selects.stream().map(this::buildSelectExpression).collect(Collectors.joining(", "));
    }

    private String buildSelectExpression(SelectField field) {
        String alias = sanitizedAlias(field.alias());

        if (field.column() == null || field.column().isBlank()) {
            if (field.aggregation() == AggregationFunction.COUNT) {
                return "COUNT(*)" + alias;
            }
            throw new InvalidQueryException("집계 함수 없이 컬럼명이 비어있습니다.");
        }

        String colRef = resolveColumnRef(field.column());

        if (field.aggregation() == null) {
            return colRef + alias;
        }

        if (field.aggregation() == AggregationFunction.COUNT_DISTINCT) {
            return "COUNT(DISTINCT " + colRef + ")" + alias;
        }
        return field.aggregation().getSqlFunction() + "(" + colRef + ")" + alias;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WHERE 절 빌더
    // ──────────────────────────────────────────────────────────────────────────

    private String buildWhereClause(LogQueryRequest request, UUID projectId, List<Object> params) {
        List<String> conditions = new ArrayList<>();

        params.add(projectId);
        conditions.add("project_id = ?");

        String timeField = request.timeField();
        if (!ALLOWED_TIME_FIELDS.contains(timeField)) {
            throw new InvalidQueryException("허용되지 않는 시간 필드: " + timeField);
        }
        params.add(request.from());
        params.add(request.to());
        conditions.add(timeField + " BETWEEN ? AND ?");

        if (request.wheres() != null && !request.wheres().isEmpty()) {
            List<String> filterClauses = new ArrayList<>();
            for (WhereFilter filter : request.wheres()) {
                filterClauses.add(buildFilterClause(filter, params));
            }
            String logic = "OR".equalsIgnoreCase(request.whereLogic()) ? " OR " : " AND ";
            conditions.add("(" + String.join(logic, filterClauses) + ")");
        }

        return "WHERE " + String.join(" AND ", conditions);
    }

    private String buildFilterClause(WhereFilter filter, List<Object> params) {
        String colRef = resolveColumnRef(filter.column());

        return switch (filter.operator()) {
            case EQ -> {
                params.add(filter.value());
                yield colRef + " = ?";
            }
            case NEQ -> {
                params.add(filter.value());
                yield colRef + " <> ?";
            }
            case GT -> {
                params.add(filter.value());
                yield colRef + " > ?";
            }
            case LT -> {
                params.add(filter.value());
                yield colRef + " < ?";
            }
            case GTE -> {
                params.add(filter.value());
                yield colRef + " >= ?";
            }
            case LTE -> {
                params.add(filter.value());
                yield colRef + " <= ?";
            }
            case BETWEEN -> {
                params.add(filter.value());
                params.add(filter.value2());
                yield colRef + " BETWEEN ? AND ?";
            }
            case NOT_BETWEEN -> {
                params.add(filter.value());
                params.add(filter.value2());
                yield colRef + " NOT BETWEEN ? AND ?";
            }
            case IS_NULL -> colRef + " IS NULL";
            case IS_NOT_NULL -> colRef + " IS NOT NULL";
            case CONTAINS -> {
                params.add("%" + filter.value() + "%");
                yield colRef + " ILIKE ?";
            }
            case NOT_CONTAINS -> {
                params.add("%" + filter.value() + "%");
                yield "NOT (" + colRef + " ILIKE ?)";
            }
            case STARTS_WITH -> {
                params.add(filter.value() + "%");
                yield colRef + " ILIKE ?";
            }
            case ENDS_WITH -> {
                params.add("%" + filter.value());
                yield colRef + " ILIKE ?";
            }
            case IS_EMPTY -> colRef + " = ''";
            case IS_NOT_EMPTY -> colRef + " <> ''";
            case ANY_IN -> {
                List<String> values = filter.values() != null ? filter.values() : List.of();
                if (values.isEmpty()) yield "1=0";
                String placeholders =
                        values.stream().map(v -> "?").collect(Collectors.joining(", "));
                params.addAll(values);
                yield colRef + " IN (" + placeholders + ")";
            }
            case NOT_IN -> {
                List<String> values = filter.values() != null ? filter.values() : List.of();
                if (values.isEmpty()) yield "1=1";
                String placeholders =
                        values.stream().map(v -> "?").collect(Collectors.joining(", "));
                params.addAll(values);
                yield colRef + " NOT IN (" + placeholders + ")";
            }
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GROUP BY / ORDER BY 빌더
    // ──────────────────────────────────────────────────────────────────────────

    private String buildGroupByClause(List<String> groupBy) {
        if (groupBy == null || groupBy.isEmpty()) {
            return "";
        }
        String cols =
                groupBy.stream().map(this::resolveColumnRef).collect(Collectors.joining(", "));
        return " GROUP BY " + cols;
    }

    private String buildOrderByClause(OrderByClause orderBy) {
        if (orderBy == null || orderBy.column() == null || orderBy.column().isBlank()) {
            return " ORDER BY occurred_at DESC";
        }
        String colRef = resolveColumnRef(orderBy.column());
        String dir =
                orderBy.direction() != null
                                && ALLOWED_DIRECTIONS.contains(orderBy.direction().toUpperCase())
                        ? orderBy.direction().toUpperCase()
                        : "ASC";
        return " ORDER BY " + colRef + " " + dir;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 컬럼 참조 변환 (화이트리스트 검증 + JSONB 경로 처리)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 컬럼 참조를 SQL 표현식으로 변환.
     *
     * <p>단순 컬럼: {@code severity} → {@code severity}<br>
     * JSONB 단일 키: {@code attributes.level} → {@code (attributes->>'level')}<br>
     * JSONB 중첩 키: {@code attributes.game.fps} → {@code (attributes#>>'{game,fps}')}
     */
    private String resolveColumnRef(String columnRef) {
        QueryableColumn col = QueryableColumn.parseColumn(columnRef);

        if (!col.isJsonb()) {
            return col.getDbName();
        }

        String[] pathParts = QueryableColumn.parseJsonbPath(columnRef);
        if (pathParts.length == 0) {
            return col.getDbName();
        }
        if (pathParts.length == 1) {
            // SQL: (attributes->>'key')
            return "((" + col.getDbName() + ")->>'" + pathParts[0] + "')";
        }
        // 중첩 경로: #>> '{game,fps}'
        // pathParts는 이미 ^[a-zA-Z0-9_]{1,64}$ 검증 완료 → 리터럴 삽입 안전
        String pathLiteral = String.join(",", pathParts);
        // SQL: (attributes#>>'{game,fps}')
        return "((" + col.getDbName() + ")#>>'" + "{" + pathLiteral + "}')";
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 유틸 메서드
    // ──────────────────────────────────────────────────────────────────────────

    private String sanitizedAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return "";
        }
        QueryableColumn.validateAlias(alias);
        return " AS " + alias;
    }

    private List<String> deriveColumnNames(List<SelectField> selects) {
        if (selects == null || selects.isEmpty()) {
            return List.of(
                    "log_id",
                    "project_id",
                    "session_id",
                    "user_id",
                    "severity",
                    "event_category",
                    "archive",
                    "occurred_at",
                    "ingested_at",
                    "trace_id",
                    "span_id",
                    "fingerprint");
        }
        return selects.stream()
                .map(
                        s -> {
                            if (s.alias() != null && !s.alias().isBlank()) return s.alias();
                            if (s.column() != null && !s.column().isBlank()) return s.column();
                            return s.aggregation() != null ? s.aggregation().name() : "value";
                        })
                .collect(Collectors.toList());
    }
}

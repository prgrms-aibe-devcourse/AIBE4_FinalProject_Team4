package kr.java.documind.domain.dashboard.service;

import java.util.List;
import kr.java.documind.domain.dashboard.model.dto.request.VisualizationConfig;
import kr.java.documind.domain.dashboard.model.dto.request.WidgetConfig;
import kr.java.documind.domain.dashboard.model.dto.request.WidgetLayout;
import kr.java.documind.domain.dashboard.model.dto.response.DashboardPresetResponse;
import kr.java.documind.domain.logexplorer.model.dto.request.LogQueryRequest;
import kr.java.documind.domain.logexplorer.model.dto.request.OrderByClause;
import kr.java.documind.domain.logexplorer.model.dto.request.SelectField;
import kr.java.documind.domain.logexplorer.model.dto.request.WhereFilter;
import kr.java.documind.domain.logexplorer.model.enums.AggregationFunction;
import kr.java.documind.domain.logexplorer.model.enums.FilterOperator;
import org.springframework.stereotype.Service;

/** 대시보드 프리셋 목록. from/to 는 실행 시 globalTimeRange 로 오버라이드됨. */
@Service
public class DashboardPresetService {

    // ── 공통 더미 쿼리 파라미터 ──────────────────────────────────────────────────

    private static final String DUMMY_TIME = "2000-01-01T00:00:00Z";

    // ── FPS 게임 프리셋 ──────────────────────────────────────────────────────────

    private static final List<WidgetConfig> FPS_WIDGETS =
            List.of(
                    // ① CRITICAL 에러 수 (stat)
                    new WidgetConfig(
                            "fps-stat-critical",
                            "CRITICAL 에러 수",
                            "stat",
                            new WidgetLayout(1, 1, 3, 2),
                            new LogQueryRequest(
                                    null,
                                    null,
                                    "occurred_at",
                                    List.of(
                                            new SelectField(
                                                    "severity",
                                                    AggregationFunction.COUNT,
                                                    "critical_count")),
                                    List.of(
                                            new WhereFilter(
                                                    "severity",
                                                    FilterOperator.EQ,
                                                    "CRITICAL",
                                                    null,
                                                    null)),
                                    "AND",
                                    List.of(),
                                    new OrderByClause(null, "DESC"),
                                    100,
                                    0),
                            new VisualizationConfig(
                                    null, List.of("critical_count"), "danger", "건", false, false)),
                    // ② 카테고리별 로그 수 (bar)
                    new WidgetConfig(
                            "fps-bar-category",
                            "카테고리별 로그 수",
                            "bar",
                            new WidgetLayout(4, 1, 5, 3),
                            new LogQueryRequest(
                                    null,
                                    null,
                                    "occurred_at",
                                    List.of(
                                            new SelectField("event_category", null, "category"),
                                            new SelectField(
                                                    "event_category",
                                                    AggregationFunction.COUNT,
                                                    "log_count")),
                                    List.of(),
                                    "AND",
                                    List.of("event_category"),
                                    new OrderByClause("log_count", "DESC"),
                                    20,
                                    0),
                            new VisualizationConfig(
                                    "category", List.of("log_count"), "primary", "건", true, false)),
                    // ③ 심각도 분포 (pie)
                    new WidgetConfig(
                            "fps-pie-severity",
                            "심각도 분포",
                            "pie",
                            new WidgetLayout(9, 1, 4, 3),
                            new LogQueryRequest(
                                    null,
                                    null,
                                    "occurred_at",
                                    List.of(
                                            new SelectField("severity", null, "severity"),
                                            new SelectField(
                                                    "severity",
                                                    AggregationFunction.COUNT,
                                                    "count")),
                                    List.of(),
                                    "AND",
                                    List.of("severity"),
                                    new OrderByClause("count", "DESC"),
                                    10,
                                    0),
                            new VisualizationConfig(
                                    "severity", List.of("count"), "default", "건", true, false)),
                    // ④ 최근 로그 (table)
                    new WidgetConfig(
                            "fps-table-recent",
                            "최근 로그",
                            "table",
                            new WidgetLayout(1, 4, 12, 4),
                            new LogQueryRequest(
                                    null,
                                    null,
                                    "occurred_at",
                                    List.of(
                                            new SelectField("occurred_at", null, "occurred_at"),
                                            new SelectField("severity", null, "severity"),
                                            new SelectField(
                                                    "event_category", null, "event_category"),
                                            new SelectField("user_id", null, "user_id")),
                                    List.of(),
                                    "AND",
                                    List.of(),
                                    new OrderByClause("occurred_at", "DESC"),
                                    50,
                                    0),
                            new VisualizationConfig(
                                    null, List.of(), "default", null, false, false)));

    // ── RPG 게임 프리셋 ──────────────────────────────────────────────────────────

    private static final List<WidgetConfig> RPG_WIDGETS =
            List.of(
                    // ① 활성 세션 수 (stat)
                    new WidgetConfig(
                            "rpg-stat-session",
                            "활성 세션 수",
                            "stat",
                            new WidgetLayout(1, 1, 3, 2),
                            new LogQueryRequest(
                                    null,
                                    null,
                                    "occurred_at",
                                    List.of(
                                            new SelectField(
                                                    "session_id",
                                                    AggregationFunction.COUNT_DISTINCT,
                                                    "active_sessions")),
                                    List.of(),
                                    "AND",
                                    List.of(),
                                    new OrderByClause(null, "DESC"),
                                    100,
                                    0),
                            new VisualizationConfig(
                                    null,
                                    List.of("active_sessions"),
                                    "primary",
                                    "세션",
                                    false,
                                    false)),
                    // ② 심각도 분포 (pie)
                    new WidgetConfig(
                            "rpg-pie-severity",
                            "심각도 분포",
                            "pie",
                            new WidgetLayout(4, 1, 4, 3),
                            new LogQueryRequest(
                                    null,
                                    null,
                                    "occurred_at",
                                    List.of(
                                            new SelectField("severity", null, "severity"),
                                            new SelectField(
                                                    "severity",
                                                    AggregationFunction.COUNT,
                                                    "count")),
                                    List.of(),
                                    "AND",
                                    List.of("severity"),
                                    new OrderByClause("count", "DESC"),
                                    10,
                                    0),
                            new VisualizationConfig(
                                    "severity", List.of("count"), "default", "건", true, false)),
                    // ③ 유저별 이벤트 Top10 (bar)
                    new WidgetConfig(
                            "rpg-bar-user-events",
                            "유저별 이벤트 Top 10",
                            "bar",
                            new WidgetLayout(8, 1, 5, 3),
                            new LogQueryRequest(
                                    null,
                                    null,
                                    "occurred_at",
                                    List.of(
                                            new SelectField("user_id", null, "user_id"),
                                            new SelectField(
                                                    "user_id",
                                                    AggregationFunction.COUNT,
                                                    "event_count")),
                                    List.of(),
                                    "AND",
                                    List.of("user_id"),
                                    new OrderByClause("event_count", "DESC"),
                                    10,
                                    0),
                            new VisualizationConfig(
                                    "user_id",
                                    List.of("event_count"),
                                    "primary",
                                    "건",
                                    false,
                                    false)),
                    // ④ 최근 로그 (table)
                    new WidgetConfig(
                            "rpg-table-recent",
                            "최근 로그",
                            "table",
                            new WidgetLayout(1, 4, 12, 4),
                            new LogQueryRequest(
                                    null,
                                    null,
                                    "occurred_at",
                                    List.of(
                                            new SelectField("occurred_at", null, "occurred_at"),
                                            new SelectField("severity", null, "severity"),
                                            new SelectField("user_id", null, "user_id"),
                                            new SelectField("session_id", null, "session_id")),
                                    List.of(),
                                    "AND",
                                    List.of(),
                                    new OrderByClause("occurred_at", "DESC"),
                                    50,
                                    0),
                            new VisualizationConfig(
                                    null, List.of(), "default", null, false, false)));

    // ── 빈 캔버스 ────────────────────────────────────────────────────────────────

    private static final List<WidgetConfig> EMPTY_WIDGETS = List.of();

    // ── 프리셋 목록 ──────────────────────────────────────────────────────────────

    private static final List<DashboardPresetResponse> PRESETS =
            List.of(
                    new DashboardPresetResponse(
                            "fps-game",
                            "FPS 게임",
                            "FPS 게임에 최적화된 대시보드. CRITICAL 에러, 카테고리 분포, 심각도 파이, 최근 로그.",
                            FPS_WIDGETS),
                    new DashboardPresetResponse(
                            "rpg-game",
                            "RPG 게임",
                            "RPG 게임에 최적화된 대시보드. 활성 세션, 심각도 분포, 유저별 이벤트 Top10, 최근 로그.",
                            RPG_WIDGETS),
                    new DashboardPresetResponse(
                            "empty", "빈 캔버스", "위젯 없이 직접 구성하는 빈 캔버스.", EMPTY_WIDGETS));

    public List<DashboardPresetResponse> getPresets() {
        return PRESETS;
    }
}

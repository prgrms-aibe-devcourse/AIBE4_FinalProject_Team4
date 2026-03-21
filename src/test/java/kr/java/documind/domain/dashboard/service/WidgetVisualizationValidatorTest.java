package kr.java.documind.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import kr.java.documind.domain.dashboard.model.dto.request.VisualizationConfig;
import kr.java.documind.domain.dashboard.model.dto.request.WidgetConfig;
import kr.java.documind.domain.dashboard.model.dto.request.WidgetLayout;
import kr.java.documind.domain.logexplorer.model.dto.request.LogQueryRequest;
import kr.java.documind.domain.logexplorer.model.dto.request.SelectField;
import kr.java.documind.domain.logexplorer.model.enums.AggregationFunction;
import kr.java.documind.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WidgetVisualizationValidator 단위 테스트")
class WidgetVisualizationValidatorTest {

    private WidgetVisualizationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WidgetVisualizationValidator();
    }

    // ── 공통 픽스처 ──────────────────────────────────────────────────────────────

    private static SelectField dim(String col) {
        return new SelectField(col, null, col);
    }

    private static SelectField metric(String col) {
        return new SelectField(col, AggregationFunction.COUNT, col + "_count");
    }

    private static VisualizationConfig anyViz() {
        return new VisualizationConfig("x", List.of("y"), "primary", null, true, false);
    }

    private static WidgetLayout anyLayout() {
        return new WidgetLayout(1, 1, 6, 4);
    }

    private static LogQueryRequest query(List<SelectField> selects, List<String> groupBy) {
        return new LogQueryRequest(
                null, null, "occurred_at", selects, List.of(), "AND", groupBy, null, 100, 0);
    }

    private static WidgetConfig widget(String type, LogQueryRequest q) {
        return new WidgetConfig("w1", "테스트", type, anyLayout(), q, anyViz());
    }

    // ── stat ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("stat 차트")
    class StatChart {

        @Test
        @DisplayName("정상: 집계 1개, groupBy 없음")
        void stat_valid() {
            var q = query(List.of(metric("severity")), List.of());
            assertThatNoException().isThrownBy(() -> validator.validate(widget("stat", q)));
        }

        @Test
        @DisplayName("예외: SELECT 없음")
        void stat_noSelect_throws() {
            var q = query(List.of(), List.of());
            assertThatThrownBy(() -> validator.validate(widget("stat", q)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("예외: 집계 없는 SELECT")
        void stat_noAggregation_throws() {
            var q = query(List.of(dim("severity")), List.of());
            assertThatThrownBy(() -> validator.validate(widget("stat", q)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("예외: groupBy 있음")
        void stat_withGroupBy_throws() {
            var q = query(List.of(metric("severity")), List.of("severity"));
            assertThatThrownBy(() -> validator.validate(widget("stat", q)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── table ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("table 차트")
    class TableChart {

        @Test
        @DisplayName("정상: SELECT 1~10개, groupBy 0개")
        void table_valid() {
            var q = query(List.of(dim("severity"), dim("user_id")), List.of());
            assertThatNoException().isThrownBy(() -> validator.validate(widget("table", q)));
        }

        @Test
        @DisplayName("예외: SELECT 없음")
        void table_noSelect_throws() {
            var q = query(List.of(), List.of());
            assertThatThrownBy(() -> validator.validate(widget("table", q)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── bar ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("bar 차트")
    class BarChart {

        @Test
        @DisplayName("정상: 차원 1개 + 지표 1개, groupBy 1개")
        void bar_valid() {
            var q =
                    query(
                            List.of(dim("event_category"), metric("severity")),
                            List.of("event_category"));
            assertThatNoException().isThrownBy(() -> validator.validate(widget("bar", q)));
        }

        @Test
        @DisplayName("예외: groupBy 2개")
        void bar_twoGroupBy_throws() {
            var q =
                    query(
                            List.of(dim("event_category"), metric("severity")),
                            List.of("event_category", "user_id"));
            assertThatThrownBy(() -> validator.validate(widget("bar", q)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("예외: 지표 4개 (최대 3개)")
        void bar_tooManyMetrics_throws() {
            var q =
                    query(
                            List.of(
                                    dim("event_category"),
                                    metric("m1"),
                                    metric("m2"),
                                    metric("m3"),
                                    metric("m4")),
                            List.of("event_category"));
            assertThatThrownBy(() -> validator.validate(widget("bar", q)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── line ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("line 차트")
    class LineChart {

        @Test
        @DisplayName("정상: 차원 1개 + 지표 2개, groupBy 1개")
        void line_valid() {
            var q =
                    query(
                            List.of(dim("occurred_at"), metric("severity"), metric("user_id")),
                            List.of("occurred_at"));
            assertThatNoException().isThrownBy(() -> validator.validate(widget("line", q)));
        }

        @Test
        @DisplayName("예외: groupBy 없음")
        void line_noGroupBy_throws() {
            var q = query(List.of(dim("occurred_at"), metric("severity")), List.of());
            assertThatThrownBy(() -> validator.validate(widget("line", q)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── pie ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("pie 차트")
    class PieChart {

        @Test
        @DisplayName("정상: 차원 1개 + 지표 1개, groupBy 1개")
        void pie_valid() {
            var q = query(List.of(dim("severity"), metric("severity")), List.of("severity"));
            assertThatNoException().isThrownBy(() -> validator.validate(widget("pie", q)));
        }

        @Test
        @DisplayName("예외: SELECT 3개 (정확히 2개 필요)")
        void pie_threeSelects_throws() {
            var q =
                    query(
                            List.of(dim("severity"), metric("severity"), metric("user_id")),
                            List.of("severity"));
            assertThatThrownBy(() -> validator.validate(widget("pie", q)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("예외: groupBy 2개")
        void pie_twoGroupBy_throws() {
            var q =
                    query(
                            List.of(dim("severity"), metric("severity")),
                            List.of("severity", "user_id"));
            assertThatThrownBy(() -> validator.validate(widget("pie", q)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── heatmap ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("heatmap 차트")
    class HeatmapChart {

        @Test
        @DisplayName("정상: 차원 2개 + 지표 1개, groupBy 2개")
        void heatmap_valid() {
            var q =
                    query(
                            List.of(dim("event_category"), dim("severity"), metric("user_id")),
                            List.of("event_category", "severity"));
            assertThatNoException().isThrownBy(() -> validator.validate(widget("heatmap", q)));
        }

        @Test
        @DisplayName("예외: SELECT 2개 (3개 필요)")
        void heatmap_twoSelects_throws() {
            var q =
                    query(
                            List.of(dim("event_category"), dim("severity")),
                            List.of("event_category", "severity"));
            assertThatThrownBy(() -> validator.validate(widget("heatmap", q)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("예외: groupBy 1개 (2개 필요)")
        void heatmap_oneGroupBy_throws() {
            var q =
                    query(
                            List.of(dim("event_category"), dim("severity"), metric("user_id")),
                            List.of("event_category"));
            assertThatThrownBy(() -> validator.validate(widget("heatmap", q)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("예외: 지표에 집계 없음")
        void heatmap_metricWithoutAggregation_throws() {
            var q =
                    query(
                            List.of(dim("event_category"), dim("severity"), dim("user_id")),
                            List.of("event_category", "severity"));
            assertThatThrownBy(() -> validator.validate(widget("heatmap", q)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── 지원하지 않는 타입 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("예외: 지원하지 않는 차트 타입")
    void unsupportedType_throws() {
        var q = query(List.of(metric("severity")), List.of());
        assertThatThrownBy(() -> validator.validate(widget("donut", q)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("donut");
    }

    // ── 공통 Null 체크 및 전역 검증 보완 ──────────────────────────────────────────

    @Test
    @DisplayName("예외: 위젯 자체가 null인 경우")
    void widget_null_throws() {
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("예외: 시각화 설정(visualization)이 null인 경우")
    void visualization_null_throws() {
        // query는 정상이지만 visualization이 null인 객체 생성
        WidgetConfig w =
                new WidgetConfig(
                        "w1",
                        "제목",
                        "stat",
                        anyLayout(),
                        query(List.of(metric("v")), List.of()),
                        null);
        assertThatThrownBy(() -> validator.validate(w)).isInstanceOf(BusinessException.class);
    }

    // ── stat 차트 보완 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("예외: SELECT 항목이 2개인 경우")
    void stat_multipleSelects_throws() {
        var q = query(List.of(metric("m1"), metric("m2")), List.of());
        assertThatThrownBy(() -> validator.validate(widget("stat", q)))
                .isInstanceOf(BusinessException.class);
    }

    // ── bar / line / pie 차원 검증 보완 ──────────────────────────────────────────

    @Test
    @DisplayName("예외: 첫 번째 항목(차원)에 집계가 포함된 경우")
    void bar_dimensionWithAggregation_throws() {
        // 차원이어야 할 첫 번째 필드에 AVG 집계를 넣은 상황
        var q = query(List.of(metric("category"), metric("value")), List.of("category"));
        assertThatThrownBy(() -> validator.validate(widget("bar", q)))
                .isInstanceOf(BusinessException.class);
    }

    // ── table 차트 경계값 보완 ───────────────────────────────────────────────────

    @Test
    @DisplayName("예외: SELECT 항목이 11개인 경우 (최대 10개)")
    void table_tooManySelects_throws() {
        var selects = IntStream.range(0, 11).mapToObj(i -> dim("col" + i)).toList();
        var q = query(selects, List.of());
        assertThatThrownBy(() -> validator.validate(widget("table", q)))
                .isInstanceOf(BusinessException.class);
    }
}

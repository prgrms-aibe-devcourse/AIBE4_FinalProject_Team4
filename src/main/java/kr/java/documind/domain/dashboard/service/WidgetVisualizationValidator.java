package kr.java.documind.domain.dashboard.service;

import java.util.List;
import kr.java.documind.domain.dashboard.model.dto.request.VisualizationConfig;
import kr.java.documind.domain.dashboard.model.dto.request.WidgetConfig;
import kr.java.documind.domain.logexplorer.model.dto.request.LogQueryRequest;
import kr.java.documind.domain.logexplorer.model.dto.request.SelectField;
import kr.java.documind.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 차트 타입별 쿼리 shape 검증.
 *
 * <table>
 *   <tr><th>type</th><th>필수 SELECT</th><th>필수 GROUP BY</th><th>aggregation</th></tr>
 *   <tr><td>stat</td><td>집계 1개</td><td>0</td><td>필수</td></tr>
 *   <tr><td>table</td><td>1~10개, 자유</td><td>0~3</td><td>불필요</td></tr>
 *   <tr><td>bar</td><td>차원 1 + 지표 1~3</td><td>1 (차원과 일치)</td><td>필수</td></tr>
 *   <tr><td>line</td><td>차원 1 + 지표 1~3</td><td>1</td><td>필수</td></tr>
 *   <tr><td>pie</td><td>차원 1 + 지표 정확히 1</td><td>1</td><td>필수</td></tr>
 *   <tr><td>heatmap</td><td>차원 2 + 지표 정확히 1</td><td>2</td><td>필수</td></tr>
 * </table>
 */
@Component
public class WidgetVisualizationValidator {

    private static final List<String> SUPPORTED_TYPES =
            List.of("stat", "table", "bar", "line", "pie", "heatmap");

    public void validate(WidgetConfig widget) {
        if (widget == null) {
            throw invalid("위젯 설정이 null입니다.");
        }
        String type = widget.type();
        if (type == null || !SUPPORTED_TYPES.contains(type)) {
            throw invalid("지원하지 않는 차트 타입입니다: " + type);
        }
        LogQueryRequest query = widget.query();
        if (query == null) {
            throw invalid("위젯 쿼리가 null입니다.");
        }
        VisualizationConfig viz = widget.visualization();
        if (viz == null) {
            throw invalid("시각화 설정이 null입니다.");
        }

        switch (type) {
            case "stat" -> validateStat(query);
            case "table" -> validateTable(query);
            case "bar", "line" -> validateBarOrLine(query, type);
            case "pie" -> validatePie(query);
            case "heatmap" -> validateHeatmap(query);
        }
    }

    // ── Type-specific validators ───────────────────────────────────────────────

    private void validateStat(LogQueryRequest query) {
        List<SelectField> selects = query.selects();
        if (selects == null || selects.isEmpty()) {
            throw invalid("stat 위젯은 집계 SELECT 항목이 1개 필요합니다.");
        }
        if (selects.size() != 1) {
            throw invalid("stat 위젯은 SELECT 항목이 정확히 1개이어야 합니다.");
        }
        if (!hasAggregation(selects.get(0))) {
            throw invalid("stat 위젯의 SELECT 항목에 집계 함수가 필요합니다.");
        }
        List<String> groupBy = query.groupBy();
        if (groupBy != null && !groupBy.isEmpty()) {
            throw invalid("stat 위젯은 GROUP BY를 사용할 수 없습니다.");
        }
    }

    private void validateTable(LogQueryRequest query) {
        List<SelectField> selects = query.selects();
        if (selects == null || selects.isEmpty()) {
            throw invalid("table 위젯은 SELECT 항목이 1개 이상 필요합니다.");
        }
        if (selects.size() > 10) {
            throw invalid("table 위젯은 SELECT 항목이 최대 10개입니다.");
        }
        // GROUP BY는 0~3개 허용 (쿼리 레벨 @Size(max=3)로 이미 제한됨)
    }

    private void validateBarOrLine(LogQueryRequest query, String type) {
        List<SelectField> selects = query.selects();
        if (selects == null || selects.size() < 2) {
            throw invalid(type + " 위젯은 차원 1개 + 지표 1개 이상의 SELECT 항목이 필요합니다.");
        }
        SelectField dimension = selects.get(0);
        if (hasAggregation(dimension)) {
            throw invalid(type + " 위젯의 첫 번째 SELECT 항목은 차원(집계 없음)이어야 합니다.");
        }
        int metricCount = selects.size() - 1;
        if (metricCount < 1 || metricCount > 3) {
            throw invalid(type + " 위젯의 지표는 1~3개이어야 합니다.");
        }
        for (int i = 1; i < selects.size(); i++) {
            if (!hasAggregation(selects.get(i))) {
                throw invalid(type + " 위젯의 지표 SELECT 항목에 집계 함수가 필요합니다.");
            }
        }
        List<String> groupBy = query.groupBy();
        if (groupBy == null || groupBy.size() != 1) {
            throw invalid(type + " 위젯은 GROUP BY 항목이 정확히 1개이어야 합니다.");
        }
    }

    private void validatePie(LogQueryRequest query) {
        List<SelectField> selects = query.selects();
        if (selects == null || selects.size() != 2) {
            throw invalid("pie 위젯은 차원 1개 + 지표 정확히 1개의 SELECT 항목이 필요합니다.");
        }
        if (hasAggregation(selects.get(0))) {
            throw invalid("pie 위젯의 첫 번째 SELECT 항목은 차원(집계 없음)이어야 합니다.");
        }
        if (!hasAggregation(selects.get(1))) {
            throw invalid("pie 위젯의 두 번째 SELECT 항목에 집계 함수가 필요합니다.");
        }
        List<String> groupBy = query.groupBy();
        if (groupBy == null || groupBy.size() != 1) {
            throw invalid("pie 위젯은 GROUP BY 항목이 정확히 1개이어야 합니다.");
        }
    }

    private void validateHeatmap(LogQueryRequest query) {
        List<SelectField> selects = query.selects();
        if (selects == null || selects.size() != 3) {
            throw invalid("heatmap 위젯은 차원 2개 + 지표 정확히 1개의 SELECT 항목이 필요합니다.");
        }
        if (hasAggregation(selects.get(0)) || hasAggregation(selects.get(1))) {
            throw invalid("heatmap 위젯의 처음 두 SELECT 항목은 차원(집계 없음)이어야 합니다.");
        }
        if (!hasAggregation(selects.get(2))) {
            throw invalid("heatmap 위젯의 세 번째 SELECT 항목에 집계 함수가 필요합니다.");
        }
        List<String> groupBy = query.groupBy();
        if (groupBy == null || groupBy.size() != 2) {
            throw invalid("heatmap 위젯은 GROUP BY 항목이 정확히 2개이어야 합니다.");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean hasAggregation(SelectField field) {
        return field != null && field.aggregation() != null;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}

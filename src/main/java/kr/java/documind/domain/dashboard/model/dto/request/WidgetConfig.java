package kr.java.documind.domain.dashboard.model.dto.request;

import kr.java.documind.domain.logexplorer.model.dto.request.LogQueryRequest;

/**
 * 대시보드 위젯 설정. JSONB layoutConfig 배열의 원소.
 *
 * <p>query 의 from/to 는 실행 시 globalTimeRange 로 동적 오버라이드되므로, 저장 시 null 허용.
 */
public record WidgetConfig(
        String id,
        String title,
        String type,
        WidgetLayout layout,
        LogQueryRequest query,
        VisualizationConfig visualization) {}

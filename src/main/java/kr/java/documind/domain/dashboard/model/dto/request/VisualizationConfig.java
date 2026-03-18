package kr.java.documind.domain.dashboard.model.dto.request;

import java.util.List;

/** 위젯 차트 시각화 설정. */
public record VisualizationConfig(
        String xAxis,
        List<String> yAxis,
        String colorScheme,
        String unit,
        boolean showLegend,
        boolean stacked) {}

package kr.java.documind.domain.dashboard.model.dto.response;

import java.util.List;
import kr.java.documind.domain.dashboard.model.dto.request.WidgetConfig;

public record DashboardPresetResponse(
        String id, String name, String description, List<WidgetConfig> layoutConfig) {}

package kr.java.documind.domain.dashboard.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DashboardViewCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        List<WidgetConfig> layoutConfig,
        String globalTimeRange,
        Integer refreshIntervalMs) {}

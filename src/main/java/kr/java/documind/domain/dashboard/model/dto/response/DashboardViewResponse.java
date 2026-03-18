package kr.java.documind.domain.dashboard.model.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.dashboard.model.dto.request.WidgetConfig;
import kr.java.documind.domain.dashboard.model.entity.DashboardView;

public record DashboardViewResponse(
        UUID id,
        String name,
        String description,
        List<WidgetConfig> layoutConfig,
        String globalTimeRange,
        Integer refreshIntervalMs,
        boolean defaultView,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static DashboardViewResponse from(DashboardView view) {
        return new DashboardViewResponse(
                view.getId(),
                view.getName(),
                view.getDescription(),
                view.getLayoutConfig(),
                view.getGlobalTimeRange(),
                view.getRefreshIntervalMs(),
                view.isDefaultView(),
                view.getCreatedAt(),
                view.getUpdatedAt());
    }
}

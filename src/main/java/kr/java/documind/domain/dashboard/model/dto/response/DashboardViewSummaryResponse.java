package kr.java.documind.domain.dashboard.model.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import kr.java.documind.domain.dashboard.model.entity.DashboardView;

public record DashboardViewSummaryResponse(
        UUID id,
        String name,
        String description,
        int widgetCount,
        String globalTimeRange,
        Integer refreshIntervalMs,
        boolean defaultView,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static DashboardViewSummaryResponse from(DashboardView view) {
        return new DashboardViewSummaryResponse(
                view.getId(),
                view.getName(),
                view.getDescription(),
                view.getLayoutConfig() != null ? view.getLayoutConfig().size() : 0,
                view.getGlobalTimeRange(),
                view.getRefreshIntervalMs(),
                view.isDefaultView(),
                view.getCreatedAt(),
                view.getUpdatedAt());
    }
}

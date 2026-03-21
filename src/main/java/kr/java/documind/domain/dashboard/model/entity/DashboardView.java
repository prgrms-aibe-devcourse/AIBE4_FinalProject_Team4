package kr.java.documind.domain.dashboard.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.dashboard.model.dto.request.WidgetConfig;
import kr.java.documind.global.entity.UuidBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dashboard_view")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardView extends UuidBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<WidgetConfig> layoutConfig = new ArrayList<>();

    @Column(name = "global_time_range", nullable = false, length = 20)
    private String globalTimeRange;

    @Column(name = "refresh_interval_ms")
    private Integer refreshIntervalMs;

    @Column(name = "is_default", nullable = false)
    private boolean defaultView;

    public static DashboardView create(
            Project project,
            UUID createdBy,
            String name,
            String description,
            List<WidgetConfig> layoutConfig,
            String globalTimeRange,
            Integer refreshIntervalMs) {

        if (layoutConfig != null && layoutConfig.size() > 10) {
            throw new IllegalArgumentException("뷰당 위젯은 최대 10개까지만 허용됩니다.");
        }

        DashboardView view = new DashboardView();
        view.project = project;
        view.createdBy = createdBy;
        view.name = name;
        view.description = description;
        view.layoutConfig = layoutConfig != null ? layoutConfig : new ArrayList<>();
        view.globalTimeRange = globalTimeRange != null ? globalTimeRange : "1h";
        view.refreshIntervalMs = refreshIntervalMs;
        view.defaultView = false;
        return view;
    }

    public void update(
            String name,
            String description,
            List<WidgetConfig> layoutConfig,
            String globalTimeRange,
            Integer refreshIntervalMs) {
        if (layoutConfig != null && layoutConfig.size() > 10) {
            throw new IllegalArgumentException("뷰당 위젯은 최대 10개까지만 허용됩니다.");
        }

        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        this.description = description;
        this.layoutConfig = layoutConfig != null ? layoutConfig : new ArrayList<>();
        if (globalTimeRange != null) {
            this.globalTimeRange = globalTimeRange;
        }
        this.refreshIntervalMs = refreshIntervalMs;
    }

    public void markAsDefault() {
        this.defaultView = true;
    }

    public void unmarkAsDefault() {
        this.defaultView = false;
    }
}

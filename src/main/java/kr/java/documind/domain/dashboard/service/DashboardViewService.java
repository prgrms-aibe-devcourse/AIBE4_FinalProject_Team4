package kr.java.documind.domain.dashboard.service;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import kr.java.documind.domain.dashboard.exception.DashboardLimitExceededException;
import kr.java.documind.domain.dashboard.exception.DashboardViewNotFoundException;
import kr.java.documind.domain.dashboard.model.dto.request.DashboardViewCreateRequest;
import kr.java.documind.domain.dashboard.model.dto.request.DashboardViewUpdateRequest;
import kr.java.documind.domain.dashboard.model.dto.request.WidgetConfig;
import kr.java.documind.domain.dashboard.model.dto.response.DashboardViewResponse;
import kr.java.documind.domain.dashboard.model.dto.response.DashboardViewSummaryResponse;
import kr.java.documind.domain.dashboard.model.entity.DashboardView;
import kr.java.documind.domain.dashboard.model.repository.DashboardViewRepository;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardViewService {

    static final int MAX_VIEWS_PER_PROJECT = 50;
    static final int MAX_WIDGETS_PER_VIEW = 10;
    static final int MIN_REFRESH_INTERVAL_MS = 10_000;

    private final DashboardViewRepository dashboardViewRepository;
    private final ProjectRepository projectRepository;
    private final WidgetVisualizationValidator visualizationValidator;

    // ── Read ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DashboardViewSummaryResponse> listViews(UUID projectId) {
        return dashboardViewRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(DashboardViewSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardViewResponse getView(UUID projectId, UUID viewId) {
        DashboardView view = findViewOrThrow(projectId, viewId);
        return DashboardViewResponse.from(view);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public DashboardViewResponse createView(
            UUID projectId, UUID memberId, DashboardViewCreateRequest request) {
        validateViewLimit(projectId);
        validateWidgets(request.layoutConfig());
        validateRefreshInterval(request.refreshIntervalMs());

        Project project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(() -> new NotFoundException("프로젝트를 찾을 수 없습니다: " + projectId));

        DashboardView view =
                DashboardView.create(
                        project,
                        memberId,
                        request.name(),
                        request.description(),
                        request.layoutConfig(),
                        request.globalTimeRange(),
                        request.refreshIntervalMs());

        return DashboardViewResponse.from(dashboardViewRepository.save(view));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public DashboardViewResponse updateView(
            UUID projectId, UUID viewId, DashboardViewUpdateRequest request) {
        validateWidgets(request.layoutConfig());
        validateRefreshInterval(request.refreshIntervalMs());

        DashboardView view = findViewOrThrow(projectId, viewId);
        view.update(
                request.name(),
                request.description(),
                request.layoutConfig(),
                request.globalTimeRange(),
                request.refreshIntervalMs());

        return DashboardViewResponse.from(view);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteView(UUID projectId, UUID viewId) {
        DashboardView view = findViewOrThrow(projectId, viewId);
        dashboardViewRepository.delete(view);
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    private void validateViewLimit(UUID projectId) {
        long count = dashboardViewRepository.countByProjectId(projectId);
        if (count >= MAX_VIEWS_PER_PROJECT) {
            throw new DashboardLimitExceededException(
                    "프로젝트당 대시보드 뷰는 최대 " + MAX_VIEWS_PER_PROJECT + "개까지 생성할 수 있습니다.");
        }
    }

    private void validateWidgets(List<WidgetConfig> widgets) {
        if (widgets == null || widgets.isEmpty()) {
            return;
        }
        if (widgets.size() > MAX_WIDGETS_PER_VIEW) {
            throw new DashboardLimitExceededException(
                    "뷰당 위젯은 최대 " + MAX_WIDGETS_PER_VIEW + "개까지 추가할 수 있습니다.");
        }
        for (WidgetConfig widget : widgets) {
            visualizationValidator.validate(widget);
        }
    }

    private void validateRefreshInterval(Integer refreshIntervalMs) {
        if (refreshIntervalMs != null && refreshIntervalMs < MIN_REFRESH_INTERVAL_MS) {
            throw new DashboardLimitExceededException(
                    "자동 새로고침 주기는 최소 " + MIN_REFRESH_INTERVAL_MS + "ms 이상이어야 합니다.");
        }
    }

    private DashboardView findViewOrThrow(UUID projectId, UUID viewId) {
        return dashboardViewRepository
                .findByIdAndProjectId(viewId, projectId)
                .orElseThrow(() -> new DashboardViewNotFoundException(viewId.toString()));
    }
}

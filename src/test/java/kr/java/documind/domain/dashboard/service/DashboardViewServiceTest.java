package kr.java.documind.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import kr.java.documind.domain.dashboard.exception.DashboardLimitExceededException;
import kr.java.documind.domain.dashboard.exception.DashboardViewNotFoundException;
import kr.java.documind.domain.dashboard.model.dto.request.DashboardViewCreateRequest;
import kr.java.documind.domain.dashboard.model.dto.request.DashboardViewUpdateRequest;
import kr.java.documind.domain.dashboard.model.dto.response.DashboardViewResponse;
import kr.java.documind.domain.dashboard.model.entity.DashboardView;
import kr.java.documind.domain.dashboard.model.repository.DashboardViewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardViewService 단위 테스트")
class DashboardViewServiceTest {

    @InjectMocks private DashboardViewService dashboardViewService;

    @Mock private DashboardViewRepository dashboardViewRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private WidgetVisualizationValidator visualizationValidator;

    // ── fixtures ────────────────────────────────────────────────────────────────

    private static UUID projectId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private static UUID memberId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000002");
    }

    private static UUID viewId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000003");
    }

    private static DashboardView stubView(UUID id, UUID projectId) {
        DashboardView view =
                DashboardView.create(null, memberId(), "테스트 뷰", null, List.of(), "1h", null);
        ReflectionTestUtils.setField(view, "id", id);
        return view;
    }

    private static Project stubProject() {
        return Mockito.mock(Project.class);
    }

    // ── 뷰 목록 조회 ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listViews()")
    class ListViews {

        @Test
        @DisplayName("정상: 프로젝트 뷰 목록을 생성일 역순으로 반환한다")
        void listViews_whenExists_returnsSortedList() {
            // Given
            UUID pid = projectId();
            DashboardView view = stubView(viewId(), pid);
            given(dashboardViewRepository.findByProjectIdOrderByCreatedAtDesc(pid))
                    .willReturn(List.of(view));

            // When
            var result = dashboardViewService.listViews(pid);

            // Then
            assertThat(result).hasSize(1);
        }
    }

    // ── 뷰 생성 ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createView()")
    class CreateView {

        @Test
        @DisplayName("정상: 위젯 없는 빈 뷰를 생성한다")
        void createView_withNoWidgets_succeeds() {
            // Given
            UUID pid = projectId();
            UUID mid = memberId();
            Project project = stubProject();
            DashboardViewCreateRequest request =
                    new DashboardViewCreateRequest("내 대시보드", "설명", List.of(), "1h", null);

            given(dashboardViewRepository.countByProjectId(pid)).willReturn(0L);
            given(projectRepository.findById(pid)).willReturn(Optional.of(project));
            DashboardView saved =
                    DashboardView.create(project, mid, "내 대시보드", "설명", List.of(), "1h", null);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            given(dashboardViewRepository.save(any())).willReturn(saved);

            // When
            DashboardViewResponse response = dashboardViewService.createView(pid, mid, request);

            // Then
            assertThat(response.name()).isEqualTo("내 대시보드");
            then(dashboardViewRepository).should().save(any(DashboardView.class));
        }

        @Test
        @DisplayName("예외: 프로젝트 뷰 수가 한도(50)에 달하면 DashboardLimitExceededException 발생")
        void createView_whenViewLimitReached_throwsException() {
            // Given
            UUID pid = projectId();
            given(dashboardViewRepository.countByProjectId(pid))
                    .willReturn((long) DashboardViewService.MAX_VIEWS_PER_PROJECT);

            // When / Then
            assertThatThrownBy(
                            () ->
                                    dashboardViewService.createView(
                                            pid,
                                            memberId(),
                                            new DashboardViewCreateRequest(
                                                    "뷰", null, List.of(), "1h", null)))
                    .isInstanceOf(DashboardLimitExceededException.class)
                    .hasMessageContaining("50");
        }

        @Test
        @DisplayName("예외: 위젯 수가 한도(10)를 초과하면 DashboardLimitExceededException 발생")
        void createView_whenWidgetLimitExceeded_throwsException() {
            // Given
            UUID pid = projectId();
            given(dashboardViewRepository.countByProjectId(pid)).willReturn(0L);

            var widgets =
                    java.util.stream.IntStream.rangeClosed(1, 11)
                            .mapToObj(i -> buildTableWidget("w-" + i))
                            .toList();

            // When / Then
            assertThatThrownBy(
                            () ->
                                    dashboardViewService.createView(
                                            pid,
                                            memberId(),
                                            new DashboardViewCreateRequest(
                                                    "뷰", null, widgets, "1h", null)))
                    .isInstanceOf(DashboardLimitExceededException.class)
                    .hasMessageContaining("10");
        }

        @Test
        @DisplayName("예외: 자동 새로고침 주기가 최솟값(10000ms) 미만이면 DashboardLimitExceededException 발생")
        void createView_whenRefreshIntervalTooShort_throwsException() {
            // Given
            UUID pid = projectId();
            given(dashboardViewRepository.countByProjectId(pid)).willReturn(0L);

            // When / Then
            assertThatThrownBy(
                            () ->
                                    dashboardViewService.createView(
                                            pid,
                                            memberId(),
                                            new DashboardViewCreateRequest(
                                                    "뷰", null, List.of(), "1h", 5000)))
                    .isInstanceOf(DashboardLimitExceededException.class)
                    .hasMessageContaining("10000");
        }
    }

    // ── 뷰 수정 ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateView()")
    class UpdateView {

        @Test
        @DisplayName("정상: 뷰 이름과 위젯 목록을 업데이트한다")
        void updateView_withValidRequest_succeeds() {
            // Given
            UUID pid = projectId();
            UUID vid = viewId();
            DashboardView view = stubView(vid, pid);
            given(dashboardViewRepository.findByIdAndProjectId(vid, pid))
                    .willReturn(Optional.of(view));

            DashboardViewUpdateRequest request =
                    new DashboardViewUpdateRequest("수정된 뷰", null, List.of(), "6h", null);

            // When
            DashboardViewResponse response = dashboardViewService.updateView(pid, vid, request);

            // Then
            assertThat(response.name()).isEqualTo("수정된 뷰");
            assertThat(response.globalTimeRange()).isEqualTo("6h");
        }

        @Test
        @DisplayName("예외: 존재하지 않는 뷰 ID로 수정 시 DashboardViewNotFoundException 발생")
        void updateView_whenViewNotFound_throwsException() {
            // Given
            UUID pid = projectId();
            UUID vid = viewId();
            given(dashboardViewRepository.findByIdAndProjectId(vid, pid))
                    .willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(
                            () ->
                                    dashboardViewService.updateView(
                                            pid,
                                            vid,
                                            new DashboardViewUpdateRequest(
                                                    "뷰", null, List.of(), "1h", null)))
                    .isInstanceOf(DashboardViewNotFoundException.class);
        }
    }

    // ── 뷰 삭제 ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteView()")
    class DeleteView {

        @Test
        @DisplayName("정상: 뷰를 삭제한다")
        void deleteView_whenExists_deletesSuccessfully() {
            // Given
            UUID pid = projectId();
            UUID vid = viewId();
            DashboardView view = stubView(vid, pid);
            given(dashboardViewRepository.findByIdAndProjectId(vid, pid))
                    .willReturn(Optional.of(view));

            // When
            dashboardViewService.deleteView(pid, vid);

            // Then
            then(dashboardViewRepository).should().delete(view);
        }

        @Test
        @DisplayName("예외: 존재하지 않는 뷰 삭제 시 DashboardViewNotFoundException 발생")
        void deleteView_whenNotFound_throwsException() {
            // Given
            UUID pid = projectId();
            UUID vid = viewId();
            given(dashboardViewRepository.findByIdAndProjectId(vid, pid))
                    .willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> dashboardViewService.deleteView(pid, vid))
                    .isInstanceOf(DashboardViewNotFoundException.class);
        }
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private kr.java.documind.domain.dashboard.model.dto.request.WidgetConfig buildTableWidget(
            String id) {
        var query =
                new kr.java.documind.domain.logexplorer.model.dto.request.LogQueryRequest(
                        null,
                        null,
                        "occurred_at",
                        List.of(
                                new kr.java.documind.domain.logexplorer.model.dto.request
                                        .SelectField("severity", null, "severity")),
                        List.of(),
                        "AND",
                        List.of(),
                        null,
                        50,
                        0);
        return new kr.java.documind.domain.dashboard.model.dto.request.WidgetConfig(
                id,
                "테스트",
                "table",
                new kr.java.documind.domain.dashboard.model.dto.request.WidgetLayout(1, 1, 6, 4),
                query,
                new kr.java.documind.domain.dashboard.model.dto.request.VisualizationConfig(
                        null, List.of(), "default", null, false, false));
    }
}

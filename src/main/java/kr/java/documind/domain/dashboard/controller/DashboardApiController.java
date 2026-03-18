package kr.java.documind.domain.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.dashboard.model.dto.request.DashboardViewCreateRequest;
import kr.java.documind.domain.dashboard.model.dto.request.DashboardViewUpdateRequest;
import kr.java.documind.domain.dashboard.model.dto.response.DashboardPresetResponse;
import kr.java.documind.domain.dashboard.model.dto.response.DashboardViewResponse;
import kr.java.documind.domain.dashboard.model.dto.response.DashboardViewSummaryResponse;
import kr.java.documind.domain.dashboard.service.DashboardPresetService;
import kr.java.documind.domain.dashboard.service.DashboardViewService;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "대시보드 API")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class DashboardApiController {

    private final DashboardViewService dashboardViewService;
    private final DashboardPresetService dashboardPresetService;

    @Operation(summary = "대시보드 뷰 목록 조회")
    @RequireProjectMember
    @GetMapping("/{publicId}/dashboard/views")
    public ResponseEntity<ApiResponse<List<DashboardViewSummaryResponse>>> listViews(
            @CurrentProject ProjectRequestContext ctx) {
        List<DashboardViewSummaryResponse> views = dashboardViewService.listViews(ctx.projectId());
        return ResponseEntity.ok(ApiResponse.success(views));
    }

    @Operation(summary = "대시보드 뷰 생성")
    @RequireProjectMember
    @PostMapping("/{publicId}/dashboard/views")
    public ResponseEntity<ApiResponse<DashboardViewResponse>> createView(
            @CurrentProject ProjectRequestContext ctx,
            @Valid @RequestBody DashboardViewCreateRequest request) {
        DashboardViewResponse response =
                dashboardViewService.createView(ctx.projectId(), ctx.actorMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "대시보드 뷰 상세 조회")
    @RequireProjectMember
    @GetMapping("/{publicId}/dashboard/views/{viewId}")
    public ResponseEntity<ApiResponse<DashboardViewResponse>> getView(
            @CurrentProject ProjectRequestContext ctx, @PathVariable UUID viewId) {
        DashboardViewResponse response = dashboardViewService.getView(ctx.projectId(), viewId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "대시보드 뷰 저장 (전체 교체)")
    @RequireProjectMember
    @PutMapping("/{publicId}/dashboard/views/{viewId}")
    public ResponseEntity<ApiResponse<DashboardViewResponse>> updateView(
            @CurrentProject ProjectRequestContext ctx,
            @PathVariable UUID viewId,
            @Valid @RequestBody DashboardViewUpdateRequest request) {
        DashboardViewResponse response =
                dashboardViewService.updateView(ctx.projectId(), viewId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "대시보드 뷰 삭제")
    @RequireProjectMember
    @DeleteMapping("/{publicId}/dashboard/views/{viewId}")
    public ResponseEntity<ApiResponse<Void>> deleteView(
            @CurrentProject ProjectRequestContext ctx, @PathVariable UUID viewId) {
        dashboardViewService.deleteView(ctx.projectId(), viewId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "대시보드 프리셋 목록 조회")
    @RequireProjectMember
    @GetMapping("/{publicId}/dashboard/presets")
    public ResponseEntity<ApiResponse<List<DashboardPresetResponse>>> getPresets(
            @CurrentProject ProjectRequestContext ctx) {
        List<DashboardPresetResponse> presets = dashboardPresetService.getPresets();
        return ResponseEntity.ok(ApiResponse.success(presets));
    }
}

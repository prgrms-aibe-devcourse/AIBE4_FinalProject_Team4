package kr.java.documind.domain.logexplorer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.logexplorer.model.dto.request.LogQueryRequest;
import kr.java.documind.domain.logexplorer.model.dto.response.LogColumnResponse;
import kr.java.documind.domain.logexplorer.model.dto.response.LogQueryResponse;
import kr.java.documind.domain.logexplorer.service.LogColumnMetadataService;
import kr.java.documind.domain.logexplorer.service.LogExplorerQueryService;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.QueryRateLimit;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Log Explorer", description = "로그 탐색기 API")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class LogExplorerApiController {

    private final LogExplorerQueryService logExplorerQueryService;
    private final LogColumnMetadataService logColumnMetadataService;

    @Operation(summary = "조회 가능 컬럼 목록", description = "정적 컬럼 및 JSONB 동적 키 목록을 반환합니다.")
    @RequireProjectMember
    @GetMapping("/{publicId}/logs/columns")
    public ResponseEntity<ApiResponse<LogColumnResponse>> getColumns(
            @CurrentProject ProjectRequestContext ctx) {

        LogColumnResponse response = logColumnMetadataService.getColumnMetadata(ctx.projectId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "로그 탐색 쿼리 실행", description = "동적 필터, 집계, 정렬을 포함한 로그 조회를 수행합니다. 분당 20회 제한.")
    @RequireProjectMember
    @QueryRateLimit
    @PostMapping("/{publicId}/logs/query")
    public ResponseEntity<ApiResponse<LogQueryResponse>> executeQuery(
            @CurrentProject ProjectRequestContext ctx,
            @Valid @RequestBody LogQueryRequest request) {

        LogQueryResponse response = logExplorerQueryService.query(ctx.projectId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

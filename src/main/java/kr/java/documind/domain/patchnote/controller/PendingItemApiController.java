package kr.java.documind.domain.patchnote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.patchnote.model.dto.FeedQuery;
import kr.java.documind.domain.patchnote.model.dto.PendingItemDetail;
import kr.java.documind.domain.patchnote.model.dto.PendingItemSummary;
import kr.java.documind.domain.patchnote.model.enums.FeedMode;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.service.PendingItemCommandService;
import kr.java.documind.domain.patchnote.service.PendingItemQueryService;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 패치노트 Pending Item 관리 API.
 *
 * <p>피드 목록/상세 조회 및 제외/복원 상태 전환을 제공한다.
 * 모든 엔드포인트는 {@code @RequireProjectMember}를 통해 프로젝트 멤버만 접근할 수 있다.
 */
@Tag(name = "PatchNote Feed", description = "패치노트 피드(Pending Item) 관리 API")
@RestController
@RequestMapping("/api/projects/{publicId}/patch-note/pending-items")
@RequiredArgsConstructor
@RequireProjectMember
public class PendingItemApiController {

    private final PendingItemQueryService pendingItemQueryService;
    private final PendingItemCommandService pendingItemCommandService;

    /**
     * Pending Item 피드 목록 조회.
     */
    @Operation(
            summary = "패치노트 피드 목록 조회",
            description =
                    "프로젝트의 Pending Item 목록을 조회합니다. "
                            + "키워드 검색(title·summary·초성), 소스 타입·패치 분류·날짜 범위 필터, "
                            + "탐색기 모드(EXCLUDED / COMPLETED)를 지원합니다. "
                            + "정렬: source_created_at DESC, id DESC.")
    @PendingItemSwaggerDocs.GetFeedDocs
    @GetMapping
    public ResponseEntity<ApiResponse<List<PendingItemSummary>>> getFeed(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "소스 타입 (DOCUMENT / ISSUE)", example = "DOCUMENT")
                    @RequestParam(required = false)
                    SourceType sourceType,
            @Parameter(description = "패치 분류 (NEW / CHANGE / FIX / MAINTENANCE)", example = "CHANGE")
                    @RequestParam(required = false)
                    PatchType patchType,
            @Parameter(description = "소스 생성일 범위 시작 (ISO 8601)", example = "2025-01-01T00:00:00Z")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime from,
            @Parameter(description = "소스 생성일 범위 종료 (ISO 8601)", example = "2025-12-31T23:59:59Z")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime to,
            @Parameter(description = "검색어 (title / summary / 초성)", example = "몬스터")
                    @RequestParam(required = false)
                    String keyword,
            @Parameter(
                            description = "조회 모드: PENDING(기본) / EXCLUDED(탐색기) / COMPLETED(탐색기)",
                            example = "PENDING")
                    @RequestParam(required = false)
                    FeedMode mode) {

        FeedQuery query = new FeedQuery(sourceType, patchType, from, to, keyword, mode);
        List<PendingItemSummary> feed = pendingItemQueryService.getFeed(ctx.projectId(), query);
        return ResponseEntity.ok(ApiResponse.success(feed));
    }

    /**
     * Pending Item 단건 상세 조회.
     *
     * <p>sourceLink는 원본 소스가 존재하면 해당 페이지 URL을, 삭제된 경우 null을 반환한다.
     * 프론트엔드는 sourceLink null 여부로 링크 활성/비활성을 결정한다.
     *
     * @param ctx 프로젝트 컨텍스트 (인터셉터 주입)
     * @param itemId 조회 대상 PendingItem ID
     */
    @Operation(
            summary = "패치노트 피드 항목 상세 조회",
            description =
                    "Pending Item의 상세 정보를 조회합니다. "
                            + "sourceLink는 원본 소스(문서/이슈) 페이지 URL이며, "
                            + "원본이 삭제된 경우(sourceDeleted=true) null을 반환합니다.")
    @PendingItemSwaggerDocs.GetItemDetailDocs
    @GetMapping("/{itemId}")
    public ResponseEntity<ApiResponse<PendingItemDetail>> getDetail(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "PendingItem ID", example = "42", required = true)
                    @PathVariable
                    Long itemId) {

        PendingItemDetail detail =
                pendingItemQueryService.getDetail(ctx.projectId(), itemId, ctx.publicId());
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    /**
     * Pending Item 제외.
     *
     * <p>PENDING -> EXCLUDED 상태 전환. 패치노트 생성 시 해당 항목을 건너뛴다.
     *
     * @param ctx 프로젝트 컨텍스트 (인터셉터 주입)
     * @param itemId 대상 PendingItem ID
     */
    @Operation(
            summary = "패치노트 피드 항목 제외",
            description =
                    "PENDING 상태의 항목을 패치노트 피드에서 제외합니다(PENDING -> EXCLUDED). "
                            + "이미 EXCLUDED이거나 COMPLETED인 항목에는 400을 반환합니다.")
    @PendingItemSwaggerDocs.ExcludeItemDocs
    @PatchMapping("/{itemId}/exclude")
    public ResponseEntity<ApiResponse<Void>> excludeItem(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "PendingItem ID", example = "42", required = true)
                    @PathVariable
                    Long itemId) {

        pendingItemCommandService.exclude(ctx.projectId(), itemId);
        return ResponseEntity.ok(ApiResponse.success("패치노트 피드에서 제외되었습니다."));
    }

    /**
     * 제외된 Pending Item 복원.
     *
     * <p>EXCLUDED -> PENDING 상태 전환. 복원된 항목은 다음 패치노트 생성 시 다시 포함 대상이 된다.
     *
     * @param ctx 프로젝트 컨텍스트 (인터셉터 주입)
     * @param itemId 대상 PendingItem ID
     */
    @Operation(
            summary = "제외된 항목 복원",
            description =
                    "EXCLUDED 상태의 항목을 패치노트 피드로 복원합니다(EXCLUDED -> PENDING). "
                            + "EXCLUDED 상태가 아닌 항목에는 400을 반환합니다.")
    @PendingItemSwaggerDocs.RestoreItemDocs
    @PatchMapping("/{itemId}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreItem(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "PendingItem ID", example = "42", required = true)
                    @PathVariable
                    Long itemId) {

        pendingItemCommandService.restore(ctx.projectId(), itemId);
        return ResponseEntity.ok(ApiResponse.success("패치노트 피드로 복원되었습니다."));
    }
}

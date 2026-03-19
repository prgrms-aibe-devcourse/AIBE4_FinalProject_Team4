package kr.java.documind.domain.patchnote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteCreateRequest;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteDetail;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteSummary;
import kr.java.documind.domain.patchnote.service.PatchNoteCommandService;
import kr.java.documind.domain.patchnote.service.PatchNoteQueryService;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 패치노트 CRUD API.
 *
 * <p>저장·목록 조회·단건 조회·삭제를 제공한다.
 * 모든 엔드포인트는 {@code @RequireProjectMember}를 통해 프로젝트 멤버만 접근할 수 있다.
 */
@Tag(name = "PatchNote", description = "패치노트 관리 API")
@RestController
@RequestMapping("/api/projects/{publicId}/patch-note")
@RequiredArgsConstructor
@RequireProjectMember
public class PatchNoteApiController {

    private final PatchNoteCommandService patchNoteCommandService;
    private final PatchNoteQueryService patchNoteQueryService;

    /**
     * 패치노트 저장.
     *
     * <p>SSE 스트리밍({@code done} 이벤트)으로 받은 {@code cleanedContent}를 그대로 저장한다.
     * 저장 시 지정된 {@code itemIds}의 PendingItem을 COMPLETED 처리한다.
     *
     * @param ctx     프로젝트 컨텍스트
     * @param request 저장 요청 (title, content, version, itemIds)
     * @return 생성된 패치노트 ID
     */
    @Operation(
            summary = "패치노트 저장",
            description =
                    "SSE 스트리밍 완료 후 cleanedContent를 DRAFT 상태로 저장합니다. "
                            + "itemIds에 포함된 PendingItem은 COMPLETED 처리됩니다. "
                            + "버전이 중복되면 409를 반환합니다.")
    @PatchNoteSwaggerDocs.SavePatchNoteDocs
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> savePatchNote(
            @CurrentProject ProjectRequestContext ctx,
            @Valid @RequestBody PatchNoteCreateRequest request) {

        Long id = patchNoteCommandService.savePatchNote(ctx.projectId(), request);
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id)));
    }

    /**
     * 패치노트 목록 조회.
     *
     * <p>soft delete 항목 제외, 생성일시 내림차순.
     *
     * @param ctx 프로젝트 컨텍스트
     * @return 패치노트 요약 목록
     */
    @Operation(
            summary = "패치노트 목록 조회",
            description = "프로젝트의 활성 패치노트를 최신순으로 조회합니다. (삭제된 항목 제외)")
    @PatchNoteSwaggerDocs.ListPatchNotesDocs
    @GetMapping
    public ResponseEntity<ApiResponse<List<PatchNoteSummary>>> listPatchNotes(
            @CurrentProject ProjectRequestContext ctx) {

        List<PatchNoteSummary> list = patchNoteQueryService.listPatchNotes(ctx.projectId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /**
     * 패치노트 단건 상세 조회.
     *
     * @param ctx 프로젝트 컨텍스트
     * @param id  조회 대상 패치노트 ID
     * @return 패치노트 상세 (본문 포함)
     */
    @Operation(
            summary = "패치노트 단건 조회",
            description = "패치노트 ID로 상세 정보를 조회합니다. 본문(content)은 source 태그가 제거된 정제 컨텐츠입니다.")
    @PatchNoteSwaggerDocs.GetPatchNoteDetailDocs
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PatchNoteDetail>> getPatchNote(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "패치노트 ID", example = "1", required = true) @PathVariable
                    Long id) {

        PatchNoteDetail detail = patchNoteQueryService.getDetail(ctx.projectId(), id);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    /**
     * 패치노트 삭제 (soft delete).
     *
     * <p>DRAFT 상태 패치노트를 삭제한다. 이미 삭제된 항목에 대해서는 404를 반환한다.
     *
     * @param ctx 프로젝트 컨텍스트
     * @param id  삭제 대상 패치노트 ID
     */
    @Operation(
            summary = "패치노트 삭제",
            description = "패치노트를 soft delete합니다. 이미 삭제된 항목에 대해서는 404를 반환합니다.")
    @PatchNoteSwaggerDocs.DeletePatchNoteDocs
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePatchNote(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "패치노트 ID", example = "1", required = true) @PathVariable
                    Long id) {

        patchNoteCommandService.deletePatchNote(ctx.projectId(), id);
        return ResponseEntity.ok(ApiResponse.success("패치노트가 삭제되었습니다."));
    }
}

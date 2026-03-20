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

@Tag(name = "PatchNote", description = "패치노트 관리 API")
@RestController
@RequestMapping("/api/projects/{publicId}/patch-note")
@RequiredArgsConstructor
@RequireProjectMember
public class PatchNoteApiController {

    private final PatchNoteCommandService patchNoteCommandService;
    private final PatchNoteQueryService patchNoteQueryService;

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

    @Operation(summary = "패치노트 목록 조회", description = "프로젝트의 활성 패치노트를 최신순으로 조회합니다. (삭제된 항목 제외)")
    @PatchNoteSwaggerDocs.ListPatchNotesDocs
    @GetMapping
    public ResponseEntity<ApiResponse<List<PatchNoteSummary>>> listPatchNotes(
            @CurrentProject ProjectRequestContext ctx) {

        List<PatchNoteSummary> list = patchNoteQueryService.listPatchNotes(ctx.projectId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

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

    @Operation(
            summary = "패치노트 삭제",
            description = "패치노트를 soft delete합니다. 이미 삭제된 항목에 대해서는 404를 반환합니다.")
    @PatchNoteSwaggerDocs.DeletePatchNoteDocs
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deletePatchNote(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "패치노트 ID", example = "1", required = true) @PathVariable
                    Long id) {

        patchNoteCommandService.deletePatchNote(ctx.projectId(), id);
        return ResponseEntity.ok(ApiResponse.success("패치노트가 삭제되었습니다."));
    }
}

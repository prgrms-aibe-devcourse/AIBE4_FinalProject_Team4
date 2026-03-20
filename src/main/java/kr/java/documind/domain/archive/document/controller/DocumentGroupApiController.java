package kr.java.documind.domain.archive.document.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.dto.request.CategoryUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.GroupNameUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.NewVersionDocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentGroupResponse;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentMetadataResponse;
import kr.java.documind.domain.archive.document.service.DocumentGroupService;
import kr.java.documind.domain.archive.document.service.DocumentMetadataService;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.ProjectId;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.response.PageResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{publicId}/groups")
@RequireProjectMember
@RequiredArgsConstructor
public class DocumentGroupApiController {

    private final DocumentGroupService documentGroupService;
    private final DocumentMetadataService documentMetadataService;

    @GetMapping
    public ApiResponse<List<DocumentGroupResponse>> getDocumentGroups(
            @ProjectId UUID projectId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(sort = "groupName") Pageable pageable) {
        Page<DocumentGroupResponse> groups =
                documentGroupService.getDocumentGroups(projectId, keyword, pageable);
        return PageResponses.of(groups);
    }

    @GetMapping("/{groupId}/documents")
    public ApiResponse<List<DocumentMetadataResponse>> getDocumentsByGroup(
            @ProjectId UUID projectId, @PathVariable Long groupId) {
        List<DocumentMetadataResponse> documents =
                documentGroupService.getDocumentsByGroup(projectId, groupId);
        return ApiResponse.success(documents);
    }

    @PostMapping("/{groupId}/documents")
    public ResponseEntity<ApiResponse<DocumentMetadataResponse>> uploadDocumentToGroup(
            @CurrentProject ProjectRequestContext context,
            @PathVariable Long groupId,
            @RequestPart("request") @Valid NewVersionDocumentUploadRequest request,
            @RequestPart("file") MultipartFile file) {
        DocumentMetadataResponse response =
                documentMetadataService.uploadDocumentToGroup(
                        context.projectId(), context.actorMemberId(), groupId, request, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/{groupId}/groupName")
    public ApiResponse<Void> updateGroupName(
            @ProjectId UUID projectId,
            @PathVariable Long groupId,
            @RequestBody @Valid GroupNameUpdateRequest request) {
        documentGroupService.updateGroupName(projectId, groupId, request.groupName());
        return ApiResponse.success();
    }

    @PatchMapping("/{groupId}/category")
    public ApiResponse<Void> updateCategory(
            @ProjectId UUID projectId,
            @PathVariable Long groupId,
            @RequestBody @Valid CategoryUpdateRequest request) {
        documentGroupService.updateCategory(projectId, groupId, request.category());
        return ApiResponse.success();
    }
}

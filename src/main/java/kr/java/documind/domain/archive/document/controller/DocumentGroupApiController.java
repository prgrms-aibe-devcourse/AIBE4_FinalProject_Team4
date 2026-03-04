package kr.java.documind.domain.archive.document.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.dto.request.CategoryUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.GroupNameUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.NewVersionDocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentMetadataResponse;
import kr.java.documind.domain.archive.document.service.DocumentGroupService;
import kr.java.documind.domain.archive.document.service.DocumentMetadataService;
import kr.java.documind.global.annotation.ProjectId;
import kr.java.documind.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{publicId}/groups")
@RequiredArgsConstructor
public class DocumentGroupApiController {

    private final DocumentGroupService documentGroupService;
    private final DocumentMetadataService documentMetadataService;

    @GetMapping("/{groupId}/documents")
    public ApiResponse<List<DocumentMetadataResponse>> getDocumentVersions(
            @ProjectId UUID projectId, @PathVariable Long groupId) {
        List<DocumentMetadataResponse> documents =
                documentGroupService.getDocumentVersions(groupId);
        return ApiResponse.success(documents);
    }

    @PostMapping("/{groupId}/documents")
    public ResponseEntity<ApiResponse<DocumentMetadataResponse>> uploadNewVersion(
            @ProjectId UUID projectId,
            @PathVariable Long groupId,
            @RequestPart("request") @Valid NewVersionDocumentUploadRequest request,
            @RequestPart("file") MultipartFile file) {
        DocumentMetadataResponse response =
                documentMetadataService.uploadNewVersion(groupId, request, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/{groupId}/groupName")
    public ApiResponse<Void> updateGroupName(
            @ProjectId UUID projectId,
            @PathVariable Long groupId,
            @RequestBody @Valid GroupNameUpdateRequest request) {
        documentGroupService.updateGroupName(groupId, request);
        return ApiResponse.success();
    }

    @PatchMapping("/{groupId}/category")
    public ApiResponse<Void> updateGroupCategory(
            @ProjectId UUID projectId,
            @PathVariable Long groupId,
            @RequestBody @Valid CategoryUpdateRequest request) {
        documentGroupService.updateGroupCategory(groupId, request);
        return ApiResponse.success();
    }
}

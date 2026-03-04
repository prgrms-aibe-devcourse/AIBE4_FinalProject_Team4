package kr.java.documind.domain.archive.document.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentDownloadResult;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentMetadataResponse;
import kr.java.documind.domain.archive.document.service.DocumentMetadataService;
import kr.java.documind.global.annotation.ProjectId;
import kr.java.documind.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{publicId}/documents")
@RequiredArgsConstructor
public class DocumentMetadataApiController {

    private final DocumentMetadataService documentMetadataService;

    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @ProjectId UUID projectId, @PathVariable Long documentId) {
        return buildFileResponse(documentId, "attachment");
    }

    @GetMapping("/{documentId}/preview")
    public ResponseEntity<Resource> previewDocument(
            @ProjectId UUID projectId, @PathVariable Long documentId) {
        return buildFileResponse(documentId, "inline");
    }

    private ResponseEntity<Resource> buildFileResponse(Long documentId, String disposition) {
        DocumentDownloadResult result = documentMetadataService.downloadDocument(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition + "; filename=\"" + result.downloadFilename() + "\"")
                .body(result.resource());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DocumentMetadataResponse>> uploadDocument(
            @ProjectId UUID projectId,
            @RequestPart("request") @Valid DocumentUploadRequest request,
            @RequestPart("file") MultipartFile file) {
        DocumentMetadataResponse response =
                documentMetadataService.uploadDocument(projectId, request, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/{documentId}")
    public ApiResponse<Void> updateDocument(
            @ProjectId UUID projectId,
            @PathVariable Long documentId,
            @RequestPart("request") @Valid DocumentUpdateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        documentMetadataService.updateDocument(documentId, request, file);
        return ApiResponse.success();
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> deleteDocument(
            @ProjectId UUID projectId, @PathVariable Long documentId) {
        documentMetadataService.deleteDocument(documentId);
        return ApiResponse.success();
    }
}

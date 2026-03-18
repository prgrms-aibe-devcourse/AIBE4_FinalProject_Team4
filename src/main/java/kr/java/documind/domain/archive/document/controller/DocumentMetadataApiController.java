package kr.java.documind.domain.archive.document.controller;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentMetadataResponse;
import kr.java.documind.domain.archive.document.model.vo.DocumentDownloadResult;
import kr.java.documind.domain.archive.document.service.DocumentMetadataService;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;
import kr.java.documind.domain.archive.vector.service.EtlService;
import kr.java.documind.global.annotation.ProjectId;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/projects/{publicId}/documents")
@RequiredArgsConstructor
public class DocumentMetadataApiController {

    private final DocumentMetadataService documentMetadataService;
    private final EtlService etlService;

    @PostMapping
    @RequireProjectMember
    public ResponseEntity<ApiResponse<DocumentMetadataResponse>> uploadDocumentWithNewGroup(
            @ProjectId UUID projectId,
            @RequestPart("request") @Valid DocumentUploadRequest request,
            @RequestPart("file") MultipartFile file) {
        DocumentMetadataResponse response =
                documentMetadataService.uploadDocumentWithNewGroup(projectId, request, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{documentId}/download")
    @RequireProjectMember
    public ResponseEntity<Resource> downloadDocument(
            @ProjectId UUID projectId, @PathVariable Long documentId) {
        return buildFileResponse(projectId, documentId, "attachment");
    }

    @GetMapping("/{documentId}/preview")
    @RequireProjectMember
    public ResponseEntity<Resource> previewDocument(
            @ProjectId UUID projectId, @PathVariable Long documentId) {
        return buildFileResponse(projectId, documentId, "inline");
    }

    @GetMapping(
            value = "/{documentId}/embedding-status",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeEmbeddingStatus(
            @ProjectId UUID projectId, @PathVariable Long documentId) {
        EmbeddingStatus embeddingStatus =
                documentMetadataService.getEmbeddingStatus(projectId, documentId);
        return etlService.subscribeEmbeddingStatus(documentId, embeddingStatus);
    }

    @PatchMapping("/{documentId}")
    @RequireProjectMember
    public ApiResponse<Void> updateDocument(
            @ProjectId UUID projectId,
            @PathVariable Long documentId,
            @RequestPart("request") @Valid DocumentUpdateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        documentMetadataService.updateDocument(projectId, documentId, request, file);
        return ApiResponse.success();
    }

    @DeleteMapping("/{documentId}")
    @RequireProjectMember
    public ApiResponse<Void> deleteDocument(
            @ProjectId UUID projectId, @PathVariable Long documentId) {
        documentMetadataService.deleteDocument(projectId, documentId);
        return ApiResponse.success();
    }

    private ResponseEntity<Resource> buildFileResponse(
            UUID projectId, Long documentId, String disposition) {
        DocumentDownloadResult result =
                documentMetadataService.downloadDocument(projectId, documentId);

        ContentDisposition contentDisposition =
                ("attachment".equals(disposition)
                                ? ContentDisposition.attachment()
                                : ContentDisposition.inline())
                        .filename(result.downloadFilename(), StandardCharsets.UTF_8)
                        .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(result.resource());
    }
}

package kr.java.documind.domain.archive.document.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.document.infrastructure.DocumentFileStorage;
import kr.java.documind.domain.archive.document.infrastructure.DocumentGroupManager;
import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.document.infrastructure.DocumentVectorEventPublisher;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.request.NewVersionDocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.request.VersionFields;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentDetailResponse;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentMetadataResponse;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.archive.document.model.vo.DocumentDownloadResult;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;
import kr.java.documind.global.entity.DomainSource;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.util.ChoseongUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentMetadataService {

    private final DocumentGroupManager documentGroupManager;
    private final DocumentMetadataManager documentMetadataManager;

    private final DocumentFileStorage documentFileStorage;
    private final DocumentVectorEventPublisher documentVectorEventPublisher;
    private final ChoseongUtil choseongUtil;

    public DocumentDetailResponse getDocumentDetail(UUID projectId, Long documentId) {
        DocumentMetadata documentMetadata = findMetadata(documentId, projectId);
        DocumentGroup group = documentMetadata.getDocumentGroup();

        List<DocumentMetadataResponse> versions =
                documentMetadataManager.findVersionsByGroup(group).stream()
                        .map(DocumentMetadataResponse::from)
                        .toList();

        return DocumentDetailResponse.of(documentMetadata, group, versions);
    }

    public EmbeddingStatus getEmbeddingStatus(UUID projectId, Long documentId) {
        return findMetadata(documentId, projectId).getEmbeddingStatus();
    }

    public DocumentDownloadResult downloadDocument(UUID projectId, Long documentId) {
        DocumentMetadata documentMetadata = findMetadata(documentId, projectId);
        Resource resource = documentFileStorage.load(documentMetadata.getStoredKey());
        return DocumentDownloadResult.of(resource, documentMetadata);
    }

    @Transactional
    public DocumentMetadataResponse uploadDocumentWithNewGroup(
            UUID projectId, DocumentUploadRequest request, MultipartFile file) {
        validateFile(file);

        String category = normalizeText(request.category());
        String groupName = normalizeText(request.groupName());
        boolean isProcessed = Boolean.TRUE.equals(request.isProcessed());

        documentGroupManager.validateGroupNameUniqueness(projectId, category, groupName);

        DocumentGroup group =
                documentGroupManager.save(
                        DocumentGroup.create(projectId, category, groupName, choseongUtil.extract(groupName)));

        return saveFileAndCreateMetadata(projectId, group, file, request, isProcessed);
    }

    @Transactional
    public DocumentMetadataResponse uploadDocumentToGroup(
            UUID projectId,
            Long groupId,
            NewVersionDocumentUploadRequest request,
            MultipartFile file) {
        validateFile(file);

        DocumentGroup group = documentGroupManager.getByIdAndProjectId(groupId, projectId);
        boolean isProcessed = Boolean.TRUE.equals(request.isProcessed());

        validateVersionUniqueness(group, request);

        return saveFileAndCreateMetadata(projectId, group, file, request, isProcessed);
    }

    @Transactional
    public void updateDocument(
            UUID projectId, Long documentId, DocumentUpdateRequest request, MultipartFile file) {
        DocumentMetadata documentMetadata = findMetadata(documentId, projectId);

        if (file != null && !file.isEmpty()) {
            validateFile(file);
        }

        boolean isProcessed = Boolean.TRUE.equals(request.isProcessed());

        boolean versionChanged =
                documentMetadata.getMajorVersion() != request.majorVersion()
                        || documentMetadata.getMinorVersion() != request.minorVersion()
                        || documentMetadata.getPatchVersion() != request.patchVersion();

        boolean processedChanged = documentMetadata.isProcessed() != isProcessed;

        String newHash = computeHashIfChanged(documentMetadata, file);
        boolean fileChanged = newHash != null;

        if (!versionChanged && !fileChanged && !processedChanged) {
            throw new ConflictException("문서 정보가 현재와 동일합니다.");
        }

        DocumentGroup group = documentMetadata.getDocumentGroup();

        if (versionChanged) {
            validateVersionUniqueness(group, request);
        }

        if (fileChanged) {
            validateHashUniqueness(newHash, group.getProjectId());
            replaceFile(projectId, documentMetadata, file, newHash, isProcessed);
        }

        if (versionChanged) {
            documentMetadata.updateVersion(
                    request.majorVersion(), request.minorVersion(), request.patchVersion());
        }

        if (processedChanged) {
            documentMetadata.changeProcessed(isProcessed);
        }

        documentMetadata.markModified();
    }

    @Transactional
    public void deleteDocument(UUID projectId, Long documentId) {
        DocumentMetadata documentMetadata = findMetadata(documentId, projectId);
        DocumentGroup group = documentMetadata.getDocumentGroup();

        boolean isLastDocument = documentMetadataManager.countByGroup(group) == 1;

        documentFileStorage.deleteOnCommit(documentMetadata.getStoredKey());
        documentVectorEventPublisher.deleteEvent(projectId, documentMetadata.getId());

        documentMetadataManager.delete(documentMetadata);

        if (isLastDocument) {
            documentGroupManager.delete(group);
        }
    }

    @Transactional
    public void retryEmbedding(UUID projectId, Long documentId) {
        DocumentMetadata documentMetadata = findMetadata(documentId, projectId);

        if (documentMetadata.getEmbeddingStatus() != EmbeddingStatus.FAILED) {
            throw new BadRequestException("임베딩 실패 상태인 문서만 재시도할 수 있습니다.");
        }

        documentVectorEventPublisher.retryEvent(projectId, documentMetadata);
    }

    private DocumentMetadata findMetadata(Long documentId, UUID projectId) {
        return documentMetadataManager.getByIdAndProjectId(documentId, projectId);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty() || !StringUtils.hasText(file.getOriginalFilename())) {
            throw new BadRequestException("파일이 비어있거나 파일명이 없습니다.");
        }
    }

    private void validateVersionUniqueness(DocumentGroup group, VersionFields version) {
        if (documentMetadataManager.existsByGroupAndVersion(
                group, version.majorVersion(), version.minorVersion(), version.patchVersion())) {
            throw new ConflictException(
                    String.format(
                            "문서 그룹 내에 이미 존재하는 버전(v%d.%d.%d)입니다.",
                            version.majorVersion(),
                            version.minorVersion(),
                            version.patchVersion()));
        }
    }

    private void validateHashUniqueness(String hash, UUID projectId) {
        if (documentMetadataManager.existsByProjectIdAndHash(projectId, hash)) {
            throw new ConflictException("동일한 내용의 파일이 프로젝트 내에 이미 존재합니다.");
        }
    }

    private DocumentMetadataResponse saveFileAndCreateMetadata(
            UUID projectId,
            DocumentGroup group,
            MultipartFile file,
            VersionFields version,
            boolean isProcessed) {
        String hash = documentFileStorage.computeHash(file);
        validateHashUniqueness(hash, group.getProjectId());

        DocumentFileStorage.StoredDocumentFile storedFile = documentFileStorage.store(file);
        DomainSource domainSource = documentMetadataManager.createDomainSource();
        DocumentMetadata documentMetadata =
                documentMetadataManager.save(
                        DocumentMetadata.create(
                                domainSource,
                                group,
                                storedFile.displayName(),
                                storedFile.extension(),
                                version.majorVersion(),
                                version.minorVersion(),
                                version.patchVersion(),
                                hash,
                                storedFile.size(),
                                storedFile.storedKey(),
                                isProcessed,
                                EmbeddingStatus.NONE,
                                OffsetDateTime.now(ZoneOffset.UTC)));

        documentVectorEventPublisher.createEvent(projectId, documentMetadata, isProcessed);

        return DocumentMetadataResponse.from(documentMetadata);
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private String computeHashIfChanged(DocumentMetadata documentMetadata, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String hash = documentFileStorage.computeHash(file);
        return hash.equals(documentMetadata.getHash()) ? null : hash;
    }

    private void replaceFile(
            UUID projectId, DocumentMetadata documentMetadata, MultipartFile file, String newHash,
            boolean excludeFromPatchNote) {
        DocumentFileStorage.StoredDocumentFile storedFile =
                documentFileStorage.replace(documentMetadata.getStoredKey(), file);

        documentMetadata.updateFile(
                storedFile.displayName(),
                storedFile.extension(),
                newHash,
                storedFile.size(),
                storedFile.storedKey());

        documentVectorEventPublisher.replaceEvent(projectId, documentMetadata, excludeFromPatchNote);
    }
}

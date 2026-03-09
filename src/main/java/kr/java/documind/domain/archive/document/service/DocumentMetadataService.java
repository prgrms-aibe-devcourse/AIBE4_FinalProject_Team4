package kr.java.documind.domain.archive.document.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.java.documind.domain.archive.document.infrastructure.DocumentGroupManager;
import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.request.NewVersionDocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.request.VersionFields;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentDetailResponse;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentDownloadResult;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentMetadataResponse;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.archive.document.model.event.DocumentVectorCreateEvent;
import kr.java.documind.domain.archive.document.model.event.DocumentVectorDeleteEvent;
import kr.java.documind.domain.archive.document.model.event.DocumentVectorReplaceEvent;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;
import kr.java.documind.domain.member.model.entity.Project;
import kr.java.documind.global.entity.DomainSource;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.StorageException;
import kr.java.documind.global.storage.FileStore;
import kr.java.documind.global.storage.FileStoreResult;
import kr.java.documind.global.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentMetadataService {

    private static final Set<String> EMBEDDABLE_EXTENSIONS = Set.of("pdf");

    private final DocumentGroupManager documentGroupManager;
    private final DocumentMetadataManager documentMetadataManager;

    private final FileStore fileStore;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentDetailResponse getDocumentDetail(UUID projectId, Long documentId) {
        DocumentMetadata documentMetadata = findMetadata(documentId, projectId);
        DocumentGroup group = documentMetadata.getDocumentGroup();

        List<DocumentMetadataResponse> versions =
                documentMetadataManager.findVersionsByGroup(group).stream()
                        .map(DocumentMetadataResponse::from)
                        .toList();

        return DocumentDetailResponse.of(documentMetadata, group, versions);
    }

    public DocumentDownloadResult downloadDocument(UUID projectId, Long documentId) {
        DocumentMetadata documentMetadata = findMetadata(documentId, projectId);
        Resource resource = fileStore.load(documentMetadata.getStoredKey());
        return DocumentDownloadResult.of(resource, documentMetadata);
    }

    @Transactional
    public DocumentMetadataResponse uploadDocument(
            UUID projectId, DocumentUploadRequest request, MultipartFile file) {
        validateFile(file);

        Project project = documentGroupManager.findProjectById(projectId);

        documentGroupManager.validateGroupNameUniqueness(
                projectId, request.category(), request.groupName());

        // TODO: 초성 유틸 구현 후 빈 문자열을 실제 초성으로 교체
        DocumentGroup group =
                documentGroupManager.save(
                        DocumentGroup.create(project, request.category(), request.groupName(), ""));

        return saveFileAndCreateMetadata(group, file, request);
    }

    @Transactional
    public DocumentMetadataResponse uploadNewVersionDocument(
            UUID projectId,
            Long groupId,
            NewVersionDocumentUploadRequest request,
            MultipartFile file) {
        validateFile(file);

        DocumentGroup group = documentGroupManager.findByIdAndProjectId(groupId, projectId);

        validateVersionUniqueness(group, request);

        return saveFileAndCreateMetadata(group, file, request);
    }

    @Transactional
    public void updateDocument(
            UUID projectId, Long documentId, DocumentUpdateRequest request, MultipartFile file) {
        DocumentMetadata documentMetadata = findMetadata(documentId, projectId);

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
            validateHashUniqueness(newHash, documentMetadata.getDocumentGroup().getProjectId());
            replaceFile(documentMetadata, file, newHash);
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
        String storedKey = documentMetadata.getStoredKey();

        fileStore.deleteOnCommit(storedKey);
        eventPublisher.publishEvent(new DocumentVectorDeleteEvent(documentMetadata.getId()));

        documentMetadataManager.delete(documentMetadata);

        if (documentMetadataManager.countByGroup(group) == 0) {
            documentGroupManager.delete(group);
        }
    }

    // ==================== private ====================

    private DocumentMetadata findMetadata(Long documentId, UUID projectId) {
        return documentMetadataManager.findByIdAndProjectId(documentId, projectId);
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
            DocumentGroup group, MultipartFile file, VersionFields version) {
        String hash = FileUtil.computeSha256(file);
        validateHashUniqueness(hash, group.getProjectId());

        FileStoreResult storeResult = fileStore.save(file);
        fileStore.deleteOnRollback(storeResult.storedKey());

        String displayName = extractDisplayName(file);
        String extension = storeResult.extension();

        // TODO: 초성 유틸 구현 후 빈 문자열을 실제 초성으로 교체
        DomainSource domainSource = documentMetadataManager.createDomainSource();
        boolean isProcessed = Boolean.TRUE.equals(version.isProcessed());

        DocumentMetadata documentMetadata =
                documentMetadataManager.save(
                        DocumentMetadata.builder()
                                .domainSource(domainSource)
                                .documentGroup(group)
                                .documentName(displayName)
                                .choseong("")
                                .extension(extension)
                                .majorVersion(version.majorVersion())
                                .minorVersion(version.minorVersion())
                                .patchVersion(version.patchVersion())
                                .hash(hash)
                                .size(file.getSize())
                                .storedKey(storeResult.storedKey())
                                .isProcessed(isProcessed)
                                .embeddingStatus(EmbeddingStatus.NONE)
                                .uploadedAt(LocalDateTime.now())
                                .build());

        handleEmbeddingAfterSave(documentMetadata, file, extension, false);

        return DocumentMetadataResponse.from(documentMetadata);
    }

    private String extractDisplayName(MultipartFile file) {
        return StringUtils.stripFilenameExtension(file.getOriginalFilename());
    }

    private String computeHashIfChanged(DocumentMetadata documentMetadata, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String hash = FileUtil.computeSha256(file);
        return hash.equals(documentMetadata.getHash()) ? null : hash;
    }

    private void replaceFile(
            DocumentMetadata documentMetadata, MultipartFile file, String newHash) {
        FileStoreResult storeResult = fileStore.save(file);
        fileStore.deleteOnRollback(storeResult.storedKey());

        String oldStoredKey = documentMetadata.getStoredKey();
        String displayName = extractDisplayName(file);
        String extension = storeResult.extension();

        // TODO: 초성 유틸 구현 후 빈 문자열을 실제 초성으로 교체
        documentMetadata.updateFile(
                displayName, "", extension, newHash, file.getSize(), storeResult.storedKey());

        fileStore.deleteOnCommit(oldStoredKey);

        handleEmbeddingAfterSave(documentMetadata, file, extension, true);
    }

    private boolean isEmbeddable(String extension) {
        return extension != null && EMBEDDABLE_EXTENSIONS.contains(extension.toLowerCase());
    }

    private void handleEmbeddingAfterSave(
            DocumentMetadata documentMetadata,
            MultipartFile file,
            String extension,
            boolean isReplace) {
        if (isEmbeddable(extension)) {
            documentMetadata.changeEmbeddingStatus(EmbeddingStatus.PENDING);
            Path tempFile = saveTempFile(file, extension);
            deleteTempFileOnRollback(tempFile);

            if (isReplace) {
                eventPublisher.publishEvent(
                        new DocumentVectorReplaceEvent(documentMetadata.getId(), tempFile));
            } else {
                eventPublisher.publishEvent(
                        new DocumentVectorCreateEvent(documentMetadata.getId(), tempFile));
            }
        } else {
            documentMetadata.changeEmbeddingStatus(EmbeddingStatus.NONE);

            if (isReplace) {
                eventPublisher.publishEvent(
                        new DocumentVectorDeleteEvent(documentMetadata.getId()));
            }
        }
    }

    private void deleteTempFileOnRollback(Path tempFile) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            try {
                                Files.deleteIfExists(tempFile);
                            } catch (IOException e) {
                                log.warn("롤백 후 임시 파일 삭제 실패: {}", tempFile, e);
                            }
                        }
                    }
                });
    }

    private Path saveTempFile(MultipartFile file, String extension) {
        try {
            Path tempFile = Files.createTempFile("documind-etl-", "." + extension);
            file.transferTo(tempFile);
            return tempFile;
        } catch (IOException e) {
            log.error("[ETL] 임시 파일 저장 실패", e);
            throw new StorageException("임시 파일 저장에 실패했습니다.", e);
        }
    }
}

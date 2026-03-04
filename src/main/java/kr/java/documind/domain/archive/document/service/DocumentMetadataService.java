package kr.java.documind.domain.archive.document.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.request.NewVersionDocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.request.VersionFields;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentDetailResponse;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentDownloadResult;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentMetadataResponse;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.archive.document.model.repository.DocumentGroupRepository;
import kr.java.documind.domain.archive.document.model.repository.DocumentMetadataRepository;
import kr.java.documind.global.entity.DomainSource;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import kr.java.documind.global.exception.StorageException;
import kr.java.documind.global.repository.DomainSourceRepository;
import kr.java.documind.global.storage.FileStore;
import kr.java.documind.global.util.FileUtil;
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

    private final DocumentGroupRepository documentGroupRepository;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final DomainSourceRepository domainSourceRepository;
    private final FileStore fileStore;

    // ==================== DocumentViewController ====================

    public DocumentDetailResponse getDocumentDetail(Long documentId) {
        DocumentMetadata metadata = findMetadataById(documentId);
        DocumentGroup group = metadata.getDocumentGroup();

        List<DocumentMetadataResponse> versions =
                documentMetadataRepository
                        .findByDocumentGroupOrderByMajorVersionDescMinorVersionDescPatchVersionDesc(
                                group)
                        .stream()
                        .map(DocumentMetadataResponse::from)
                        .toList();

        return DocumentDetailResponse.of(metadata, group, versions);
    }

    // ==================== DocumentMetadataApiController ====================

    public DocumentDownloadResult downloadDocument(Long documentId) {
        DocumentMetadata metadata = findMetadataById(documentId);
        Resource resource = fileStore.load(metadata.getStoredKey());
        return DocumentDownloadResult.of(resource, metadata);
    }

    @Transactional
    public DocumentMetadataResponse uploadDocument(
            UUID projectId, DocumentUploadRequest request, MultipartFile file) {
        validateFile(file);

        validateGroupNameUniqueness(projectId, request.category(), request.groupName());

        // TODO: 초성 유틸 구현 후 빈 문자열을 실제 초성으로 교체
        DocumentGroup group =
                documentGroupRepository.save(
                        DocumentGroup.create(
                                projectId, request.category(), request.groupName(), ""));

        return saveFileAndCreateMetadata(group, file, request);
    }

    @Transactional
    public void updateDocument(Long documentId, DocumentUpdateRequest request, MultipartFile file) {
        DocumentMetadata metadata = findMetadataById(documentId);

        boolean versionChanged =
                metadata.getMajorVersion() != request.majorVersion()
                        || metadata.getMinorVersion() != request.minorVersion()
                        || metadata.getPatchVersion() != request.patchVersion();

        String newHash = computeHashIfChanged(metadata, file);
        boolean fileChanged = newHash != null;

        if (!versionChanged && !fileChanged) {
            throw new ConflictException("문서 정보가 현재와 동일합니다.");
        }

        DocumentGroup group = metadata.getDocumentGroup();

        if (versionChanged) {
            validateVersionUniqueness(group, request);
        }

        if (fileChanged) {
            replaceFile(metadata, file, newHash);
        }

        if (versionChanged) {
            metadata.updateVersion(
                    request.majorVersion(), request.minorVersion(), request.patchVersion());
        }
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        DocumentMetadata metadata = findMetadataById(documentId);
        DocumentGroup group = metadata.getDocumentGroup();

        DomainSource domainSource = metadata.getDomainSource();
        documentMetadataRepository.delete(metadata);
        domainSourceRepository.delete(domainSource);
        fileStore.delete(metadata.getStoredKey());

        if (documentMetadataRepository.countByDocumentGroup(group) == 0) {
            documentGroupRepository.delete(group);
        }
    }

    // ==================== DocumentGroupApiController ====================

    @Transactional
    public DocumentMetadataResponse uploadNewVersion(
            Long groupId, NewVersionDocumentUploadRequest request, MultipartFile file) {
        validateFile(file);

        DocumentGroup group =
                documentGroupRepository
                        .findById(groupId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                String.format(
                                                        "문서 그룹(id=%d)을 찾을 수 없습니다.", groupId)));

        validateVersionUniqueness(group, request);

        return saveFileAndCreateMetadata(group, file, request);
    }

    // ==================== private ====================

    private DocumentMetadata findMetadataById(Long documentId) {
        return documentMetadataRepository
                .findById(documentId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        String.format("문서(id=%d)를 찾을 수 없습니다.", documentId)));
    }

    private void validateGroupNameUniqueness(UUID projectId, String category, String groupName) {
        if (documentGroupRepository.existsByProjectIdAndCategoryAndGroupName(
                projectId, category, groupName)) {
            throw new ConflictException(
                    String.format("카테고리(%s)에 이미 존재하는 문서 그룹명(%s)입니다.", category, groupName));
        }
    }

    private void validateVersionUniqueness(DocumentGroup group, VersionFields version) {
        if (documentMetadataRepository
                .existsByDocumentGroupAndMajorVersionAndMinorVersionAndPatchVersion(
                        group,
                        version.majorVersion(),
                        version.minorVersion(),
                        version.patchVersion())) {
            throw new ConflictException(
                    String.format(
                            "문서 그룹 내에 이미 존재하는 버전(v%d.%d.%d)입니다.",
                            version.majorVersion(),
                            version.minorVersion(),
                            version.patchVersion()));
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty() || !StringUtils.hasText(file.getOriginalFilename())) {
            throw new BadRequestException("파일이 비어있거나 파일명이 없습니다.");
        }
    }

    private String computeHashIfChanged(DocumentMetadata metadata, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            String hash = FileUtil.computeSha256(file);
            return hash.equals(metadata.getHash()) ? null : hash;
        } catch (IOException e) {
            throw new StorageException("파일 해시 계산에 실패했습니다.", e);
        }
    }

    private void replaceFile(DocumentMetadata metadata, MultipartFile file, String newHash) {
        UUID projectId = metadata.getDocumentGroup().getProjectId();
        if (documentMetadataRepository.existsByProjectIdAndHash(projectId, newHash)) {
            throw new ConflictException("동일한 내용의 파일이 프로젝트 내에 이미 존재합니다.");
        }

        try {
            String storedKey = fileStore.save(file);
            fileStore.registerRollback(storedKey);

            String oldStoredKey = metadata.getStoredKey();
            ParsedFile parsed = parseFilename(file);

            // TODO: 초성 유틸 구현 후 빈 문자열을 실제 초성으로 교체
            metadata.updateFile(
                    parsed.filename(),
                    "",
                    parsed.extension(),
                    newHash,
                    file.getSize(),
                    storedKey,
                    LocalDateTime.now());

            fileStore.delete(oldStoredKey);
        } catch (IOException e) {
            throw new StorageException("파일 업로드에 실패했습니다.", e);
        }
    }

    private record ParsedFile(String filename, String extension) {}

    private ParsedFile parseFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return new ParsedFile(
                StringUtils.stripFilenameExtension(originalFilename),
                StringUtils.getFilenameExtension(originalFilename));
    }

    private DocumentMetadataResponse saveFileAndCreateMetadata(
            DocumentGroup group, MultipartFile file, VersionFields version) {
        try {
            String storedKey = fileStore.save(file);
            fileStore.registerRollback(storedKey);

            ParsedFile parsed = parseFilename(file);
            String hash = FileUtil.computeSha256(file);

            UUID projectId = group.getProjectId();
            if (documentMetadataRepository.existsByProjectIdAndHash(projectId, hash)) {
                throw new ConflictException("동일한 내용의 파일이 프로젝트 내에 이미 존재합니다.");
            }

            // TODO: 초성 유틸 구현 후 빈 문자열을 실제 초성으로 교체
            DomainSource domainSource =
                    domainSourceRepository.save(DomainSource.create(SourceType.DOCUMENT));
            DocumentMetadata metadata =
                    documentMetadataRepository.save(
                            DocumentMetadata.create(
                                    domainSource,
                                    group,
                                    parsed.filename(),
                                    "",
                                    parsed.extension(),
                                    version.majorVersion(),
                                    version.minorVersion(),
                                    version.patchVersion(),
                                    hash,
                                    file.getSize(),
                                    storedKey,
                                    LocalDateTime.now()));

            return DocumentMetadataResponse.from(metadata);
        } catch (IOException e) {
            throw new StorageException("파일 업로드에 실패했습니다.", e);
        }
    }
}

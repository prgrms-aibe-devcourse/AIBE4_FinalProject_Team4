package kr.java.documind.domain.archive.document.infrastructure;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.archive.document.model.repository.DocumentMetadataRepository;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;
import kr.java.documind.global.entity.DomainSource;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.exception.NotFoundException;
import kr.java.documind.global.repository.DomainSourceRepository;
import kr.java.documind.global.util.ChoseongUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentMetadataManager {

    private final DocumentMetadataRepository documentMetadataRepository;
    private final DomainSourceRepository domainSourceRepository;
    private final ChoseongUtil choseongUtil;

    public DocumentMetadata getByIdAndProjectId(Long documentId, UUID projectId) {
        return documentMetadataRepository
                .findByIdAndDocumentGroupProjectId(documentId, projectId)
                .orElseThrow(() -> new NotFoundException("프로젝트에서 문서를 찾을 수 없습니다."));
    }

    public List<DocumentMetadata> findVersionsByGroup(DocumentGroup group) {
        return documentMetadataRepository.findVersionsByGroup(group);
    }

    public Map<Long, DocumentMetadata> findMapByIds(Collection<Long> ids) {
        return documentMetadataRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(DocumentMetadata::getId, Function.identity()));
    }

    public List<Long> findIdsByProjectIdAndGroupName(UUID projectId, String groupName) {
        return documentMetadataRepository.findIdsByProjectIdAndGroupName(projectId, groupName);
    }

    public List<Long> findIdsByProjectIdAndCategory(UUID projectId, String category) {
        return documentMetadataRepository.findIdsByProjectIdAndCategory(projectId, category);
    }

    /**
     * sourceId로 DocumentMetadata를 조회한다 (DocumentGroup 지연 로딩 포함).
     *
     * <p>임베딩 완료 이벤트 발행 시 문서 컨텍스트를 조회하는 데 사용된다.
     *
     * @param sourceId DomainSource.id
     */
    public Optional<DocumentMetadata> findById(Long sourceId) {
        return documentMetadataRepository.findById(sourceId);
    }

    public boolean existsByProjectIdAndHash(UUID projectId, String hash) {
        return documentMetadataRepository.existsByDocumentGroupProjectIdAndHash(projectId, hash);
    }

    public boolean existsByGroupAndVersion(
            DocumentGroup group, int majorVersion, int minorVersion, int patchVersion) {
        return documentMetadataRepository.existsVersion(
                group, majorVersion, minorVersion, patchVersion);
    }

    public long countByGroup(DocumentGroup group) {
        return documentMetadataRepository.countByDocumentGroup(group);
    }

    public DocumentMetadata createMetadata(
            DocumentGroup group,
            String documentName,
            String extension,
            int majorVersion,
            int minorVersion,
            int patchVersion,
            String hash,
            long size,
            String storedKey,
            EmbeddingStatus embeddingStatus,
            OffsetDateTime uploadedAt) {
        DomainSource domainSource =
                domainSourceRepository.save(DomainSource.create(SourceType.DOCUMENT));
        String choseong = choseongUtil.extract(documentName);
        DocumentMetadata metadata =
                DocumentMetadata.create(
                        domainSource,
                        group,
                        documentName,
                        choseong,
                        extension,
                        majorVersion,
                        minorVersion,
                        patchVersion,
                        hash,
                        size,
                        storedKey,
                        embeddingStatus,
                        uploadedAt);
        return documentMetadataRepository.save(metadata);
    }

    public void updateFile(
            DocumentMetadata metadata,
            String documentName,
            String extension,
            String hash,
            long size,
            String storedKey) {
        String choseong = choseongUtil.extract(documentName);
        metadata.updateFile(documentName, choseong, extension, hash, size, storedKey);
    }

    public void updateEmbeddingStatusIfExists(Long sourceId, EmbeddingStatus status) {
        documentMetadataRepository
                .findById(sourceId)
                .ifPresent(m -> m.changeEmbeddingStatus(status));
    }

    public DocumentMetadata save(DocumentMetadata metadata) {
        return documentMetadataRepository.save(metadata);
    }

    public void delete(DocumentMetadata metadata) {
        documentMetadataRepository.delete(metadata);
    }
}

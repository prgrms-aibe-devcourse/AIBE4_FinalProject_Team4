package kr.java.documind.domain.archive.document.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentMetadataManager {

    private final DocumentMetadataRepository documentMetadataRepository;
    private final DomainSourceRepository domainSourceRepository;

    public DocumentMetadata findByIdAndProjectId(Long documentId, UUID projectId) {
        return documentMetadataRepository
            .findByIdAndDocumentGroupProjectId(documentId, projectId)
            .orElseThrow(() -> new NotFoundException("프로젝트에서 문서를 찾을 수 없습니다."));
    }

    public List<DocumentMetadata> findVersionsByGroup(DocumentGroup group) {
        return documentMetadataRepository
            .findByDocumentGroupOrderByMajorVersionDescMinorVersionDescPatchVersionDesc(group);
    }

    public Map<Long, DocumentMetadata> findByIds(Collection<Long> ids) {
        return documentMetadataRepository.findByIdIn(ids).stream()
            .collect(Collectors.toMap(DocumentMetadata::getId, Function.identity()));
    }

    public boolean existsByProjectIdAndHash(UUID projectId, String hash) {
        return documentMetadataRepository.existsByProjectIdAndHash(projectId, hash);
    }

    public boolean existsByGroupAndVersion(
        DocumentGroup group, int majorVersion, int minorVersion, int patchVersion) {
        return documentMetadataRepository
            .existsByDocumentGroupAndMajorVersionAndMinorVersionAndPatchVersion(
                group, majorVersion, minorVersion, patchVersion);
    }

    public long countByGroup(DocumentGroup group) {
        return documentMetadataRepository.countByDocumentGroup(group);
    }

    public DomainSource createDomainSource() {
        return domainSourceRepository.save(DomainSource.create(SourceType.DOCUMENT));
    }

    public DocumentMetadata save(DocumentMetadata metadata) {
        return documentMetadataRepository.save(metadata);
    }

    public void delete(DocumentMetadata metadata) {
        documentMetadataRepository.delete(metadata);
    }

    public void updateEmbeddingStatusIfExists(Long sourceId, EmbeddingStatus status) {
        documentMetadataRepository
            .findById(sourceId)
            .ifPresent(m -> m.changeEmbeddingStatus(status));
    }
}

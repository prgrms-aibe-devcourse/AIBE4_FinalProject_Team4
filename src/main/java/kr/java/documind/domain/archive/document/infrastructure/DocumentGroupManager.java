package kr.java.documind.domain.archive.document.infrastructure;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.repository.DocumentGroupRepository;
import kr.java.documind.domain.archive.document.model.repository.DocumentGroupSummary;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentGroupManager {

    private final DocumentGroupRepository documentGroupRepository;

    public DocumentGroup getByIdAndProjectId(Long groupId, UUID projectId) {
        return documentGroupRepository
                .findByIdAndProjectId(groupId, projectId)
                .orElseThrow(() -> new NotFoundException("프로젝트에서 문서 그룹을 찾을 수 없습니다."));
    }

    public Page<DocumentGroupSummary> findGroupSummaries(UUID projectId, Pageable pageable) {
        return documentGroupRepository.findGroupSummariesByProjectId(projectId, pageable);
    }

    public List<String> findDistinctGroupNames(UUID projectId) {
        return documentGroupRepository.findDistinctGroupNamesByProjectId(projectId);
    }

    public List<String> findDistinctCategories(UUID projectId) {
        return documentGroupRepository.findDistinctCategoriesByProjectId(projectId);
    }

    public void validateGroupNameUniqueness(UUID projectId, String category, String groupName) {
        if (documentGroupRepository.existsByProjectIdAndCategoryAndGroupName(
                projectId, category, groupName)) {
            throw new ConflictException(
                    String.format("문서 그룹명(%s)이 카테고리(%s)에 이미 존재합니다.", groupName, category));
        }
    }

    public DocumentGroup save(DocumentGroup group) {
        return documentGroupRepository.save(group);
    }

    public void delete(DocumentGroup group) {
        documentGroupRepository.delete(group);
    }
}

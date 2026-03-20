package kr.java.documind.domain.archive.document.service;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.document.infrastructure.DocumentGroupManager;
import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentGroupResponse;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentMetadataResponse;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.global.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentGroupService {

    private final DocumentGroupManager documentGroupManager;
    private final DocumentMetadataManager documentMetadataManager;

    public Page<DocumentGroupResponse> getDocumentGroups(UUID projectId, Pageable pageable) {
        return documentGroupManager
                .findGroupSummaries(projectId, pageable)
                .map(DocumentGroupResponse::from);
    }

    public Page<DocumentGroupResponse> getDocumentGroups(
            UUID projectId, String keyword, Pageable pageable) {
        return documentGroupManager
                .findGroupSummaries(projectId, keyword, pageable)
                .map(DocumentGroupResponse::from);
    }

    public List<DocumentMetadataResponse> getDocumentsByGroup(UUID projectId, Long groupId) {
        DocumentGroup group = getDocumentGroup(groupId, projectId);
        return documentMetadataManager.findVersionsByGroup(group).stream()
                .map(DocumentMetadataResponse::from)
                .toList();
    }

    @Transactional
    public void updateGroupName(UUID projectId, Long groupId, String groupName) {
        DocumentGroup group = getDocumentGroup(groupId, projectId);
        String normalizedGroupName = normalizeText(groupName);

        if (group.getGroupName().equals(normalizedGroupName)) {
            throw new ConflictException("문서 그룹명이 현재와 동일합니다.");
        }

        documentGroupManager.validateGroupNameUniqueness(
                group.getProjectId(), group.getCategory(), normalizedGroupName);

        group.updateGroupName(
                normalizedGroupName, documentGroupManager.extractChoseong(normalizedGroupName));
    }

    @Transactional
    public void updateCategory(UUID projectId, Long groupId, String category) {
        DocumentGroup group = getDocumentGroup(groupId, projectId);
        String normalizedCategory = normalizeText(category);

        if (group.getCategory().equals(normalizedCategory)) {
            throw new ConflictException("문서 카테고리가 현재와 동일합니다.");
        }

        documentGroupManager.validateGroupNameUniqueness(
                group.getProjectId(), normalizedCategory, group.getGroupName());

        group.updateCategory(normalizedCategory);
    }

    private DocumentGroup getDocumentGroup(Long groupId, UUID projectId) {
        return documentGroupManager.getByIdAndProjectId(groupId, projectId);
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }
}

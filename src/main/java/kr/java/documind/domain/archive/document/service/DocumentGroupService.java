package kr.java.documind.domain.archive.document.service;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.document.infrastructure.DocumentGroupManager;
import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.document.model.dto.request.CategoryUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.GroupNameUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentGroupResponse;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentMetadataResponse;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
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

    public List<DocumentMetadataResponse> getDocumentsByGroup(UUID projectId, Long groupId) {
        DocumentGroup group = findGroup(groupId, projectId);
        return documentMetadataManager.findVersionsByGroup(group).stream()
                .map(DocumentMetadataResponse::from)
                .toList();
    }

    @Transactional
    public void updateGroupName(UUID projectId, Long groupId, GroupNameUpdateRequest request) {
        DocumentGroup group = findGroup(groupId, projectId);

        documentGroupManager.validateGroupNameUniqueness(
                group.getProjectId(), group.getCategory(), request.groupName());

        // TODO: 초성 유틸 구현 후 빈 문자열을 실제 초성으로 교체
        group.updateGroupName(request.groupName(), "");
    }

    @Transactional
    public void updateCategory(UUID projectId, Long groupId, CategoryUpdateRequest request) {
        DocumentGroup group = findGroup(groupId, projectId);

        documentGroupManager.validateGroupNameUniqueness(
                group.getProjectId(), request.category(), group.getGroupName());

        group.updateCategory(request.category());
    }

    private DocumentGroup findGroup(Long groupId, UUID projectId) {
        return documentGroupManager.findByIdAndProjectId(groupId, projectId);
    }
}

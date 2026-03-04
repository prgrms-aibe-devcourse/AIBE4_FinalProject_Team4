package kr.java.documind.domain.archive.document.service;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.dto.request.CategoryUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.GroupNameUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentGroupResponse;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentMetadataResponse;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.repository.DocumentGroupRepository;
import kr.java.documind.domain.archive.document.model.repository.DocumentMetadataRepository;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentGroupService {

    private final DocumentGroupRepository documentGroupRepository;
    private final DocumentMetadataRepository documentMetadataRepository;

    // ==================== DocumentViewController ====================

    public Page<DocumentGroupResponse> getDocumentGroups(UUID projectId, Pageable pageable) {
        return documentGroupRepository
                .findGroupSummariesByProjectId(projectId, pageable)
                .map(DocumentGroupResponse::from);
    }

    // ==================== DocumentGroupApiController ====================

    public List<DocumentMetadataResponse> getDocumentVersions(Long groupId) {
        DocumentGroup group = findGroupById(groupId);
        return documentMetadataRepository
                .findByDocumentGroupOrderByMajorVersionDescMinorVersionDescPatchVersionDesc(group)
                .stream()
                .map(DocumentMetadataResponse::from)
                .toList();
    }

    @Transactional
    public void updateGroupName(Long groupId, GroupNameUpdateRequest request) {
        DocumentGroup group = findGroupById(groupId);

        if (group.getGroupName().equals(request.groupName())) {
            throw new ConflictException("문서 그룹명이 현재와 동일합니다.");
        }

        if (documentGroupRepository.existsByProjectIdAndCategoryAndGroupName(
                group.getProjectId(), group.getCategory(), request.groupName())) {
            throw new ConflictException(
                    String.format(
                            "카테고리(%s)에 이미 존재하는 문서 그룹명(%s)입니다.",
                            group.getCategory(), request.groupName()));
        }

        // TODO: 초성 유틸 구현 후 빈 문자열을 실제 초성으로 교체
        group.updateGroupName(request.groupName(), "");
    }

    @Transactional
    public void updateGroupCategory(Long groupId, CategoryUpdateRequest request) {
        DocumentGroup group = findGroupById(groupId);

        if (group.getCategory().equals(request.category())) {
            throw new ConflictException("카테고리가 현재와 동일합니다.");
        }

        if (documentGroupRepository.existsByProjectIdAndCategoryAndGroupName(
                group.getProjectId(), request.category(), group.getGroupName())) {
            throw new ConflictException(
                    String.format(
                            "카테고리(%s)에 이미 존재하는 문서 그룹명(%s)입니다.",
                            request.category(), group.getGroupName()));
        }

        group.updateCategory(request.category());
    }

    // ==================== private ====================

    private DocumentGroup findGroupById(Long groupId) {
        return documentGroupRepository
                .findById(groupId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        String.format("문서 그룹(id=%d)을 찾을 수 없습니다.", groupId)));
    }
}

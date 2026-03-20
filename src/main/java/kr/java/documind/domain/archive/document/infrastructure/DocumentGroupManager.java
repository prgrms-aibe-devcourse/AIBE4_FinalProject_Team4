package kr.java.documind.domain.archive.document.infrastructure;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.repository.DocumentGroupRepository;
import kr.java.documind.domain.archive.document.model.repository.DocumentGroupSummary;
import kr.java.documind.domain.archive.document.model.vo.DocumentGroupSummaryResult;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import kr.java.documind.global.util.ChoseongUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class DocumentGroupManager {

    private final DocumentGroupRepository documentGroupRepository;
    private final ChoseongUtil choseongUtil;

    public DocumentGroup getByIdAndProjectId(Long groupId, UUID projectId) {
        return documentGroupRepository
                .findByIdAndProjectId(groupId, projectId)
                .orElseThrow(() -> new NotFoundException("프로젝트에서 문서 그룹을 찾을 수 없습니다."));
    }

    public Page<DocumentGroupSummaryResult> findGroupSummaries(UUID projectId, Pageable pageable) {
        return documentGroupRepository
                .findGroupSummariesByProjectId(projectId, pageable)
                .map(this::toDocumentGroupSummaryResult);
    }

    public Page<DocumentGroupSummaryResult> findGroupSummaries(
            UUID projectId, String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return findGroupSummaries(projectId, pageable);
        }
        String choseong = choseongUtil.extract(keyword);
        return documentGroupRepository
                .findGroupSummariesByProjectIdAndKeyword(projectId, keyword, choseong, pageable)
                .map(this::toDocumentGroupSummaryResult);
    }

    public List<String> findDistinctGroupNames(UUID projectId) {
        return documentGroupRepository.findDistinctGroupNamesByProjectId(projectId);
    }

    public List<String> findDistinctCategories(UUID projectId) {
        return documentGroupRepository.findDistinctCategoriesByProjectId(projectId);
    }

    public DocumentGroup createGroup(UUID projectId, String category, String groupName) {
        String choseong = choseongUtil.extract(groupName);
        return DocumentGroup.create(projectId, category, groupName, choseong);
    }

    public void updateGroupName(DocumentGroup group, String groupName) {
        String choseong = choseongUtil.extract(groupName);
        group.updateGroupName(groupName, choseong);
    }

    public DocumentGroup save(DocumentGroup group) {
        return documentGroupRepository.save(group);
    }

    public void delete(DocumentGroup group) {
        documentGroupRepository.delete(group);
    }

    public void validateGroupNameUniqueness(UUID projectId, String category, String groupName) {
        if (documentGroupRepository.existsByProjectIdAndCategoryAndGroupName(
                projectId, category, groupName)) {
            throw new ConflictException(
                    String.format("문서 그룹명(%s)이 카테고리(%s)에 이미 존재합니다.", groupName, category));
        }
    }

    private DocumentGroupSummaryResult toDocumentGroupSummaryResult(DocumentGroupSummary summary) {
        return new DocumentGroupSummaryResult(
                summary.getGroupId(),
                summary.getGroupName(),
                summary.getCategory(),
                formatVersion(summary.getVersionOrdinal()),
                summary.getDocumentCount());
    }

    private String formatVersion(Long ordinal) {
        if (ordinal == null) {
            return "v0.0.0";
        }

        long major = ordinal / 1_000_000;
        long minor = (ordinal % 1_000_000) / 1_000;
        long patch = ordinal % 1_000;

        return "v" + major + "." + minor + "." + patch;
    }
}

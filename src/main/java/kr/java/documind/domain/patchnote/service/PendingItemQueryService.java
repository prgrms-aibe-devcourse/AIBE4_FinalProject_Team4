package kr.java.documind.domain.patchnote.service;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.dto.FeedQuery;
import kr.java.documind.domain.patchnote.model.dto.PendingItemDetail;
import kr.java.documind.domain.patchnote.model.dto.PendingItemSummary;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PendingItemQueryService {

    private final PendingItemRepository pendingItemRepository;

    public List<PendingItemSummary> getFeed(UUID projectId, FeedQuery query) {
        return pendingItemRepository
                .findFeed(
                        projectId,
                        query.sourceType(),
                        query.patchType(),
                        query.from(),
                        query.to(),
                        query.keyword(),
                        query.includeExcluded(),
                        query.includeCompleted())
                .stream()
                .map(PendingItemSummary::from)
                .toList();
    }

    public PendingItemDetail getDetail(UUID projectId, Long itemId, String publicId) {
        PendingItem item =
                pendingItemRepository
                        .findById(itemId)
                        .filter(i -> i.getProjectId().equals(projectId))
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Pending Item을 찾을 수 없습니다. id: " + itemId));

        String sourceLink = item.isSourceDeleted() ? null : buildSourceLink(publicId, item);
        return PendingItemDetail.from(item, sourceLink);
    }

    private String buildSourceLink(String publicId, PendingItem item) {
        return switch (item.getSourceType()) {
            case DOCUMENT -> "/projects/%s/documents/%d".formatted(publicId, item.getSourceId());
            case ISSUE -> "/projects/%s/issues/%d/analysis".formatted(publicId, item.getSourceId());
            case LOG -> throw new IllegalArgumentException(
                    "지원하지 않는 SourceType 입니다: " + item.getSourceType());
        };
    }
}

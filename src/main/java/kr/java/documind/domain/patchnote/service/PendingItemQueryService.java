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

/**
 * Pending Item 조회 서비스.
 *
 * <p>피드 목록·단건 상세 조회에 특화된 읽기 전용 서비스다. 쓰기 작업은 {@link PendingItemCommandService}에서
 * 처리한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PendingItemQueryService {

    private final PendingItemRepository pendingItemRepository;

    /**
     * Pending Item 피드 목록을 조회한다.
     *
     * <p>정렬: {@code source_created_at DESC, id DESC}
     *
     * @param projectId 프로젝트 UUID
     * @param query 필터 조건 (sourceType, patchType, 날짜 범위, 키워드, 모드)
     * @return 필터를 적용한 Pending Item 요약 목록
     */
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

    /**
     * Pending Item 단건 상세를 조회한다.
     *
     * <p>sourceLink는 원본 소스 타입과 publicId를 기반으로 생성된다. 원본이 삭제된 경우({@code
     * sourceDeleted=true}) sourceLink는 {@code null}로 반환되어 프론트엔드에서 링크를 비활성화한다.
     *
     * @param projectId 프로젝트 UUID (소유권 검증)
     * @param itemId 조회 대상 PendingItem ID
     * @param publicId 프로젝트 공개 ID (sourceLink URL 구성용)
     * @return Pending Item 상세 DTO
     * @throws NotFoundException 항목이 존재하지 않거나 다른 프로젝트에 속한 경우
     */
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

    // ──────────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 소스 타입별 원본 조회 링크를 생성한다.
     *
     * <ul>
     *   <li>DOCUMENT → {@code /projects/{publicId}/documents/{sourceId}}
     *   <li>ISSUE → {@code /projects/{publicId}/issues/{sourceId}/analysis}
     * </ul>
     */
    private String buildSourceLink(String publicId, PendingItem item) {
        return switch (item.getSourceType()) {
            case DOCUMENT ->
                    "/projects/%s/documents/%d".formatted(publicId, item.getSourceId());
            case ISSUE ->
                    "/projects/%s/issues/%d/analysis".formatted(publicId, item.getSourceId());
        };
    }
}

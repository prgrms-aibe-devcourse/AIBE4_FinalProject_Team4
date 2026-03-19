package kr.java.documind.domain.patchnote.service;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.global.enums.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PendingItem 롤백 및 원본 삭제 처리를 담당한다.
 *
 * <ul>
 *   <li>{@link #deleteForRollback} — 이슈/문서 삭제 시 pending_item 상태에 따른 처리
 *   <li>{@link #markSourceDeleted} — 원본이 삭제되었음을 pending_item에 기록
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingItemRollbackService {

    private final PendingItemRepository pendingItemRepository;

    /**
     * 소스 삭제 롤백 시 pending_item을 모두 정리한다.
     *
     * <p>동일한 {@code sourceId}에 여러 {@code changeIndex}가 존재할 수 있으므로
     * (DOCUMENT 타입의 diff 기반 항목) 모든 항목을 조회하여 상태별로 일괄 처리한다.
     *
     * <ul>
     *   <li>PENDING / EXCLUDED → bulk hard delete (벡터도 삭제 필요 → {@code true} 반환)
     *   <li>COMPLETED → {@code sourceDeleted} 플래그 일괄 처리 (벡터 삭제 불필요 → {@code false} 반환)
     *   <li>PENDING/EXCLUDED 와 COMPLETED 항목이 혼재하면 → 각각 처리 후 {@code true} 반환
     *   <li>항목 없음 → {@code false} 반환
     * </ul>
     *
     * @return PENDING/EXCLUDED 항목이 하나라도 있어 벡터 삭제가 필요하면 {@code true}
     */
    @Transactional
    public boolean deleteForRollback(UUID projectId, Long sourceId, SourceType sourceType) {
        List<PendingItem> items =
                pendingItemRepository.findAllByProjectIdAndSourceTypeAndSourceId(
                        projectId, sourceType, sourceId);

        if (items.isEmpty()) {
            return false;
        }

        boolean hasNonCompleted = items.stream().anyMatch(item -> !item.isCompleted());
        boolean hasCompleted    = items.stream().anyMatch(PendingItem::isCompleted);

        if (hasNonCompleted) {
            // PENDING / EXCLUDED 항목 bulk hard delete
            pendingItemRepository.deleteByProjectIdAndSourceTypeAndSourceIdIfNotCompleted(
                    projectId, sourceType, sourceId);
            log.debug(
                    "[PendingItem] PENDING/EXCLUDED 항목 bulk hard delete — sourceId: {}, sourceType: {}",
                    sourceId, sourceType);
        }

        if (hasCompleted) {
            // COMPLETED 항목 — 원본 삭제 플래그 일괄 처리
            // hasNonCompleted인 경우 위에서 이미 비-COMPLETED를 삭제했으므로 COMPLETED만 남아 있음
            pendingItemRepository.markSourceDeleted(projectId, sourceType, sourceId);
            log.debug(
                    "[PendingItem] COMPLETED 항목 sourceDeleted 일괄 처리 — sourceId: {}, sourceType: {}",
                    sourceId, sourceType);
        }

        return hasNonCompleted; // 벡터 삭제 필요 여부
    }

    /**
     * 원본 소스가 삭제되었음을 pending_item에 기록한다.
     *
     * <p>COMPLETED 상태 항목에서 원본이 사라진 경우 패치노트 생성 시 원본 조회 실패를 방지한다.
     */
    @Transactional
    public void markSourceDeleted(UUID projectId, Long sourceId, SourceType sourceType) {
        pendingItemRepository.markSourceDeleted(projectId, sourceType, sourceId);
        log.debug("[PendingItem] 원본 삭제 처리 - sourceId: {}, sourceType: {}", sourceId, sourceType);
    }
}

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

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingItemRollbackService {

    private final PendingItemRepository pendingItemRepository;

    @Transactional
    public boolean deleteForRollback(UUID projectId, Long sourceId, SourceType sourceType) {
        List<PendingItem> items =
                pendingItemRepository.findAllByProjectIdAndSourceTypeAndSourceId(
                        projectId, sourceType, sourceId);

        if (items.isEmpty()) {
            return false;
        }

        boolean hasNonCompleted = items.stream().anyMatch(item -> !item.isCompleted());
        boolean hasCompleted = items.stream().anyMatch(PendingItem::isCompleted);

        if (hasNonCompleted) {
            // PENDING / EXCLUDED 항목 bulk hard delete
            pendingItemRepository.deleteByProjectIdAndSourceTypeAndSourceIdIfNotCompleted(
                    projectId, sourceType, sourceId);
            log.debug(
                    "[PendingItem] PENDING/EXCLUDED 항목 bulk hard delete — sourceId: {}, sourceType: {}",
                    sourceId,
                    sourceType);
        }

        if (hasCompleted) {
            // COMPLETED 항목 — 원본 삭제 플래그 일괄 처리
            // hasNonCompleted인 경우 위에서 이미 비-COMPLETED를 삭제했으므로 COMPLETED만 남아 있음
            pendingItemRepository.markSourceDeleted(projectId, sourceType, sourceId);
            log.debug(
                    "[PendingItem] COMPLETED 항목 sourceDeleted 일괄 처리 — sourceId: {}, sourceType: {}",
                    sourceId,
                    sourceType);
        }

        return hasNonCompleted; // 벡터 삭제 필요 여부
    }

    @Transactional
    public void markSourceDeleted(UUID projectId, Long sourceId, SourceType sourceType) {
        pendingItemRepository.markSourceDeleted(projectId, sourceType, sourceId);
        log.debug("[PendingItem] 원본 삭제 처리 - sourceId: {}, sourceType: {}", sourceId, sourceType);
    }
}

package kr.java.documind.domain.patchnote.service;

import java.util.UUID;
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
     * 소스 삭제 롤백 시 pending_item을 정리한다.
     *
     * <ul>
     *   <li>PENDING / EXCLUDED → hard delete (벡터도 삭제 필요 → {@code true} 반환)
     *   <li>COMPLETED → {@code sourceDeleted} 플래그 처리 (벡터 삭제 불필요 → {@code false} 반환)
     *   <li>항목 없음 → {@code false} 반환
     * </ul>
     *
     * @return 벡터 삭제가 필요하면 {@code true}
     */
    @Transactional
    public boolean deleteForRollback(UUID projectId, Long sourceId, SourceType sourceType) {
        return pendingItemRepository
                .findByProjectIdAndSourceTypeAndSourceId(projectId, sourceType, sourceId)
                .map(
                        item -> {
                            if (item.isCompleted()) {
                                // 이미 패치노트에 사용된 항목 — 원본 삭제 플래그만 처리
                                item.markSourceDeleted();
                                log.debug(
                                        "[PendingItem] COMPLETED 항목 sourceDeleted 처리 - sourceId: {}, sourceType: {}",
                                        sourceId,
                                        sourceType);
                                return false; // 벡터 삭제 불필요
                            } else {
                                // PENDING / EXCLUDED → hard delete
                                pendingItemRepository.delete(item);
                                log.debug(
                                        "[PendingItem] {} 항목 hard delete - sourceId: {}, sourceType: {}",
                                        item.getStatus(),
                                        sourceId,
                                        sourceType);
                                return true; // 벡터도 삭제 필요
                            }
                        })
                .orElse(false); // 항목 없음
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

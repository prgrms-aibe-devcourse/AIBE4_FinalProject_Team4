package kr.java.documind.domain.patchnote.service;

import java.util.UUID;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pending Item 상태 변경 서비스.
 *
 * <ul>
 *   <li>{@link #exclude} — PENDING → EXCLUDED (패치노트 제외)
 *   <li>{@link #restore} — EXCLUDED → PENDING (제외 복원)
 * </ul>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PendingItemCommandService {

    private final PendingItemRepository pendingItemRepository;

    /**
     * Pending Item을 패치노트 피드에서 제외한다.
     *
     * <p>PENDING 상태인 항목만 제외할 수 있다. 이미 EXCLUDED이거나 COMPLETED인 경우 {@link BadRequestException}을 던진다.
     *
     * @param projectId 프로젝트 UUID (소유권 검증)
     * @param itemId 대상 PendingItem ID
     * @throws NotFoundException 항목이 존재하지 않거나 프로젝트에 속하지 않는 경우
     * @throws BadRequestException PENDING 상태가 아닌 경우
     */
    @Transactional
    public void exclude(UUID projectId, Long itemId) {
        PendingItem item = loadAndValidateOwnership(projectId, itemId);

        if (!item.isPending()) {
            throw new BadRequestException("PENDING 상태의 항목만 제외할 수 있습니다. 현재 상태: " + item.getStatus());
        }

        item.exclude();
        log.debug("[PendingItem] PENDING → EXCLUDED. itemId: {}, projectId: {}", itemId, projectId);
    }

    /**
     * 제외된 Pending Item을 피드로 복원한다.
     *
     * <p>EXCLUDED 상태인 항목만 복원할 수 있다. PENDING이거나 COMPLETED인 경우 {@link BadRequestException}을 던진다.
     *
     * @param projectId 프로젝트 UUID (소유권 검증)
     * @param itemId 대상 PendingItem ID
     * @throws NotFoundException 항목이 존재하지 않거나 프로젝트에 속하지 않는 경우
     * @throws BadRequestException EXCLUDED 상태가 아닌 경우
     */
    @Transactional
    public void restore(UUID projectId, Long itemId) {
        PendingItem item = loadAndValidateOwnership(projectId, itemId);

        if (!item.isExcluded()) {
            throw new BadRequestException(
                    "EXCLUDED 상태의 항목만 복원할 수 있습니다. 현재 상태: " + item.getStatus());
        }

        item.restore();
        log.debug("[PendingItem] EXCLUDED → PENDING. itemId: {}, projectId: {}", itemId, projectId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 공통 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * PendingItem을 로드하고 프로젝트 소유권을 검증한다.
     *
     * @throws NotFoundException 항목이 없거나 다른 프로젝트 항목인 경우
     */
    private PendingItem loadAndValidateOwnership(UUID projectId, Long itemId) {
        PendingItem item =
                pendingItemRepository
                        .findById(itemId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Pending Item을 찾을 수 없습니다. id: " + itemId));

        if (!item.getProjectId().equals(projectId)) {
            throw new NotFoundException("Pending Item을 찾을 수 없습니다. id: " + itemId);
        }

        return item;
    }
}

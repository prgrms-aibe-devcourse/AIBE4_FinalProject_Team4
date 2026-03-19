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

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PendingItemCommandService {

    private final PendingItemRepository pendingItemRepository;

    @Transactional
    public void exclude(UUID projectId, Long itemId) {
        PendingItem item = loadAndValidateOwnership(projectId, itemId);

        if (!item.isPending()) {
            throw new BadRequestException("PENDING 상태의 항목만 제외할 수 있습니다. 현재 상태: " + item.getStatus());
        }

        item.exclude();
        log.debug("[PendingItem] PENDING → EXCLUDED. itemId: {}, projectId: {}", itemId, projectId);
    }

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

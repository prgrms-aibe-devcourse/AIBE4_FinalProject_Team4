package kr.java.documind.domain.patchnote.service;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.vector.infrastructure.VectorStoreManager;
import kr.java.documind.domain.patchnote.exception.PendingItemUpsertFailedException;
import kr.java.documind.domain.patchnote.model.dto.PendingItemCreateDto;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.global.enums.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PendingItemService {

    private final PendingItemRepository pendingItemRepository;
    private final VectorStoreManager vectorStoreManager;

    @Lazy @Autowired private PendingItemService self;

    public PendingItemService(
            PendingItemRepository pendingItemRepository, VectorStoreManager vectorStoreManager) {
        this.pendingItemRepository = pendingItemRepository;
        this.vectorStoreManager = vectorStoreManager;
    }

    public void saveVectorThenUpsert(
            Long sourceId,
            List<Document> chunks,
            List<float[]> embeddings,
            PendingItemCreateDto dto) {
        // 1. 벡터 저장 (RDBMS 트랜잭션 미참여) — 실패 시 예외 전파, pending_item 미적재
        vectorStoreManager.insertChunks(sourceId, chunks, embeddings);

        // 2. pending_item upsert — self-call로 AOP 프록시(@Retryable) 경유
        self.upsertPendingItem(dto);
    }

    @Retryable(
            retryFor = DataAccessException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    @Transactional
    public void upsertPendingItem(PendingItemCreateDto dto) {
        pendingItemRepository
                .findByProjectIdAndSourceTypeAndSourceId(
                        dto.projectId(), dto.sourceType(), dto.sourceId())
                .ifPresentOrElse(
                        existing -> {
                            existing.refresh(
                                    dto.title(),
                                    dto.summary(),
                                    dto.choseong(),
                                    dto.patchType(),
                                    dto.sourceCreatedAt());
                            log.debug(
                                    "[PendingItem] refresh 완료 - sourceId: {}, sourceType: {}, 유지된 status: {}",
                                    dto.sourceId(),
                                    dto.sourceType(),
                                    existing.getStatus());
                        },
                        () -> {
                            PendingItem item =
                                    PendingItem.create(
                                            dto.projectId(),
                                            dto.sourceId(),
                                            dto.sourceType(),
                                            dto.title(),
                                            dto.summary(),
                                            dto.choseong(),
                                            dto.patchType(),
                                            dto.status(),
                                            dto.sourceCreatedAt());
                            pendingItemRepository.save(item);
                            log.debug(
                                    "[PendingItem] 신규 생성 완료 - sourceId: {}, sourceType: {}, status: {}",
                                    dto.sourceId(),
                                    dto.sourceType(),
                                    dto.status());
                        });
    }

    @Recover
    public void recoverUpsert(DataAccessException e, PendingItemCreateDto dto) {
        log.error(
                "[PendingItem] upsert 3회 재시도 모두 실패 — sourceId: {}, sourceType: {}",
                dto.sourceId(),
                dto.sourceType(),
                e);
        if (dto.sourceType() == SourceType.ISSUE) {
            // ISSUE: 패치노트 도메인이 벡터를 직접 소유 → 고아 벡터 정리
            try {
                vectorStoreManager.deleteBySourceId(dto.sourceId(), SourceType.ISSUE);
                log.info("[PendingItem] 고아 벡터 정리 완료. sourceId: {}", dto.sourceId());
            } catch (Exception deleteEx) {
                log.error(
                        "[PendingItem] 고아 벡터 정리 실패 — 수동 확인 필요. sourceId: {}",
                        dto.sourceId(),
                        deleteEx);
            }
        } else {
            // DOCUMENT 등: 벡터는 EtlService 소유 — 정리하지 않음
            log.warn(
                    "[PendingItem] {} 타입 upsert 실패 — 벡터는 EtlService 소유이므로 유지됨. sourceId: {}",
                    dto.sourceType(),
                    dto.sourceId());
        }
        // 호출 측이 PENDING_ITEM_UPSERT_FAILED 알림 이벤트를 발행할 수 있도록 예외를 전파한다.
        throw new PendingItemUpsertFailedException(dto.sourceId());
    }

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

    @Transactional
    public void markSourceDeleted(UUID projectId, Long sourceId, SourceType sourceType) {
        pendingItemRepository.markSourceDeleted(projectId, sourceType, sourceId);
        log.debug("[PendingItem] 원본 삭제 처리 - sourceId: {}, sourceType: {}", sourceId, sourceType);
    }
}

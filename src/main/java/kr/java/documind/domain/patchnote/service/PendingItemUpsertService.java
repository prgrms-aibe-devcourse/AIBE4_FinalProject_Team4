package kr.java.documind.domain.patchnote.service;

import java.util.List;
import kr.java.documind.domain.archive.vector.infrastructure.VectorStoreManager;
import kr.java.documind.domain.patchnote.exception.PendingItemUpsertFailedException;
import kr.java.documind.domain.patchnote.model.dto.PendingItemCreateRequest;
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

/**
 * PendingItem 적재 및 재시도 복구를 담당한다.
 *
 * <ul>
 *   <li>{@link #saveVectorThenUpsert} — 벡터 저장 → pending_item upsert 순서 보장
 *   <li>{@link #upsertPendingItem} — {@code @Retryable} 적용 upsert (최대 3회)
 *   <li>{@link #recoverUpsert} — 3회 실패 후 고아 벡터 정리 및 예외 전파
 * </ul>
 */
@Slf4j
@Service
public class PendingItemUpsertService {

    private final PendingItemRepository pendingItemRepository;
    private final VectorStoreManager vectorStoreManager;

    /** self-invocation으로 @Retryable AOP 프록시를 경유하기 위한 자기 참조 */
    @Lazy @Autowired private PendingItemUpsertService self;

    public PendingItemUpsertService(
            PendingItemRepository pendingItemRepository, VectorStoreManager vectorStoreManager) {
        this.pendingItemRepository = pendingItemRepository;
        this.vectorStoreManager = vectorStoreManager;
    }

    /**
     * 벡터 저장 후 pending_item을 upsert한다.
     *
     * <p>벡터 저장(RDBMS 트랜잭션 미참여)이 실패하면 예외를 전파하고 pending_item은 적재하지 않는다.
     * upsert는 self-call로 AOP 프록시(@Retryable)를 경유한다.
     */
    public void saveVectorThenUpsert(
            Long sourceId,
            List<Document> chunks,
            List<float[]> embeddings,
            PendingItemCreateRequest dto) {
        // 1. 벡터 저장 (RDBMS 트랜잭션 미참여) — 실패 시 예외 전파, pending_item 미적재
        vectorStoreManager.insertChunks(sourceId, chunks, embeddings);

        // 2. pending_item upsert — self-call로 AOP 프록시(@Retryable) 경유
        self.upsertPendingItem(dto);
    }

    /**
     * pending_item을 upsert한다.
     *
     * <p>기존 항목이 있으면 {@code refresh}로 갱신하고, 없으면 신규 생성한다.
     * {@code @Retryable}로 {@link DataAccessException} 발생 시 최대 3회 재시도한다.
     */
    @Retryable(
            retryFor = DataAccessException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    @Transactional
    public void upsertPendingItem(PendingItemCreateRequest dto) {
        pendingItemRepository
                .findByProjectIdAndSourceTypeAndSourceIdAndChangeIndex(
                        dto.projectId(), dto.sourceType(), dto.sourceId(), dto.changeIndex())
                .ifPresentOrElse(
                        existing -> {
                            existing.refresh(
                                    dto.title(),
                                    dto.summary(),
                                    dto.choseong(),
                                    dto.patchType(),
                                    dto.sourceCreatedAt(),
                                    dto.evidence(),
                                    dto.score());
                            log.debug(
                                    "[PendingItem] refresh 완료 - sourceId: {}, changeIndex: {},"
                                            + " sourceType: {}, 유지된 status: {}",
                                    dto.sourceId(),
                                    dto.changeIndex(),
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
                                            dto.sourceCreatedAt(),
                                            dto.changeIndex(),
                                            dto.evidence(),
                                            dto.score());
                            pendingItemRepository.save(item);
                            log.debug(
                                    "[PendingItem] 신규 생성 완료 - sourceId: {}, changeIndex: {},"
                                            + " sourceType: {}, status: {}",
                                    dto.sourceId(),
                                    dto.changeIndex(),
                                    dto.sourceType(),
                                    dto.status());
                        });
    }

    /**
     * {@link #upsertPendingItem} 3회 재시도 모두 실패 시 호출된다.
     *
     * <p>ISSUE 타입은 패치노트 도메인이 벡터를 직접 소유하므로 고아 벡터를 정리한다.
     * DOCUMENT 타입은 벡터가 EtlService 소유이므로 정리하지 않는다.
     * 정리 성공 여부와 무관하게 {@link PendingItemUpsertFailedException}을 전파한다.
     */
    @Recover
    public void recoverUpsert(DataAccessException e, PendingItemCreateRequest dto) {
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
}

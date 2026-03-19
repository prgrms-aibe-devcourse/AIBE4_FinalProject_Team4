package kr.java.documind.domain.patchnote.event;

import java.util.List;
import kr.java.documind.domain.archive.vector.model.repository.VectorStoreRepository;
import kr.java.documind.domain.patchnote.exception.DocumentEmbeddingEmptyException;
import kr.java.documind.domain.patchnote.infrastructure.DocumentChangeGenerator;
import kr.java.documind.domain.patchnote.infrastructure.DocumentSummaryGenerator;
import kr.java.documind.domain.patchnote.model.dto.ChunkDiffResult;
import kr.java.documind.domain.patchnote.model.dto.DocumentSummaryResult;
import kr.java.documind.domain.patchnote.model.dto.PatchCandidate;
import kr.java.documind.domain.patchnote.model.dto.PendingItemCreateRequest;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.service.DocumentDiffService;
import kr.java.documind.domain.patchnote.service.DocumentMeaningfulnessService;
import kr.java.documind.domain.patchnote.service.PatchCandidateExtractor;
import kr.java.documind.domain.patchnote.service.PendingItemUpsertService;
import kr.java.documind.domain.patchnote.util.PatchTypeResolver;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.util.ChoseongUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 문서 임베딩 완료 이벤트를 수신하여 패치노트 Pending Item을 적재한다.
 *
 * <h3>신규 문서 처리 흐름 ({@code isNewDocument=true})</h3>
 * <ol>
 *   <li>벡터 스토어에서 문서 청크 조회 (LLM 입력 + 유의미성 판단용)
 *   <li>유의미성 검증: trivial 변경이면 적재 생략
 *   <li>LLM 요약 생성: title, summary, categoryFromLlm 추출
 *   <li>PatchType 분류 → 초성 추출 → pending_item upsert (change_index=0)
 * </ol>
 *
 * <h3>업데이트 문서 처리 흐름 ({@code isNewDocument=false})</h3>
 * <ol>
 *   <li>{@link DocumentDiffService}로 이전 버전과 청크 단위 diff 계산
 *   <li>{@link PatchCandidateExtractor}로 유의미한 변경 후보 추출 및 점수화
 *   <li>후보별 LLM 호출 ({@link DocumentChangeGenerator}) → player-friendly 요약 생성
 *   <li>후보별 pending_item upsert (change_index = candidate.chunkIndex)
 * </ol>
 *
 * <ul>
 *   <li>벡터 청크 없음 → {@link DocumentEmbeddingEmptyException} throw → CustomAsyncExceptionHandler
 *       경고 알림
 *   <li>pending_item 최종 저장 실패 → {@code PendingItemUpsertFailedException} throw → 관리자 알림
 *   <li>기타 예외 → 그대로 전파 → CustomAsyncExceptionHandler 관리자 알림
 *   <li>성공 → {@link DocumentPendingItemCreatedEvent} 발행 → alarm-toast + 헤더 배지
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentVectorStatusChangedEventListener {

    /** 신규 문서 요약용 청크 조회 건수 — 문서 앞부분을 대표 텍스트로 사용 */
    private static final int CHUNK_RETRIEVAL_LIMIT = 10;

    private final VectorStoreRepository vectorStoreRepository;
    private final DocumentMeaningfulnessService meaningfulnessService;
    private final DocumentSummaryGenerator documentSummaryGenerator;
    private final DocumentDiffService documentDiffService;
    private final PatchCandidateExtractor patchCandidateExtractor;
    private final DocumentChangeGenerator documentChangeGenerator;
    private final PatchTypeResolver patchTypeResolver;
    private final PendingItemUpsertService pendingItemUpsertService;
    private final ChoseongUtil choseongUtil;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 문서 임베딩 완료 시 pending_item을 적재한다.
     *
     * <p>실패 예외는 호출 스택 상위로 전파하여 {@code CustomAsyncExceptionHandler}에 위임한다.
     * trivial 변경(유의미하지 않은 문서/후보 없음)은 예외 없이 조기 반환한다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DocumentEmbeddedEvent event) {
        log.info(
                "[PatchNote] 문서 임베딩 완료 감지 - sourceId: {}, projectId: {}, isNew: {}, exclude: {}",
                event.sourceId(),
                event.projectId(),
                event.isNewDocument(),
                event.excludeFromPatchNote());

        if (event.isNewDocument()) {
            handleNewDocument(event);
        } else {
            handleUpdatedDocument(event);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 신규 문서 처리 — 전문 LLM 요약 기반, 단일 pending_item (change_index=0)
    // ─────────────────────────────────────────────────────────────────────────

    private void handleNewDocument(DocumentEmbeddedEvent event) {
        List<String> chunks =
                vectorStoreRepository.findContentsBySourceId(
                        event.sourceId(), SourceType.DOCUMENT, CHUNK_RETRIEVAL_LIMIT);

        if (chunks.isEmpty()) {
            throw new DocumentEmbeddingEmptyException(event.sourceId());
        }

        String currentText = String.join("\n", chunks);

        // 유의미성 검증 (신규 문서는 previousText=null → 항상 true 반환 가능)
        if (!meaningfulnessService.isMeaningful(true, currentText, null)) {
            log.info("[PatchNote] 문서 변경 경미 — pending_item 미적재. sourceId: {}", event.sourceId());
            return;
        }

        DocumentSummaryResult summaryResult =
                documentSummaryGenerator.generate(
                        event.documentName(), event.documentGroupName(), event.category(), chunks);

        PatchType patchType =
                patchTypeResolver.resolveFromLlmCategory(summaryResult.categoryFromLlm());
        String choseong = choseongUtil.extract(summaryResult.title());

        PendingItemStatus status =
                event.excludeFromPatchNote()
                        ? PendingItemStatus.EXCLUDED
                        : PendingItemStatus.PENDING;

        // 신규 문서: change_index=0, evidence/score는 diff 기반 항목 전용이므로 null
        PendingItemCreateRequest dto =
                new PendingItemCreateRequest(
                        event.projectId(),
                        event.sourceId(),
                        SourceType.DOCUMENT,
                        summaryResult.title(),
                        summaryResult.summary(),
                        choseong,
                        patchType,
                        status,
                        event.sourceCreatedAt(),
                        0,
                        null,
                        null);

        pendingItemUpsertService.upsertPendingItem(dto);

        // affects_player 메타데이터 업데이트 (벡터 reranking 필터용)
        updateAffectsPlayerBestEffort(event.sourceId(), summaryResult.affectsPlayer());

        log.info(
                "[PatchNote] 신규 문서 pending_item 적재 완료 - sourceId: {}, patchType: {}, status: {}",
                event.sourceId(),
                patchType,
                status);

        eventPublisher.publishEvent(
                new DocumentPendingItemCreatedEvent(
                        event.sourceId(), event.projectId(), summaryResult.title(), status));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 업데이트 문서 처리 — diff 기반, 후보별 pending_item (change_index = chunkIndex)
    // ─────────────────────────────────────────────────────────────────────────

    private void handleUpdatedDocument(DocumentEmbeddedEvent event) {
        // 1. 이전 버전과 청크 단위 diff 계산
        List<ChunkDiffResult> diffs =
                documentDiffService.computeDiff(event.sourceId(), event.documentGroupId());

        // 2. 유의미한 변경 후보 추출
        List<PatchCandidate> candidates = patchCandidateExtractor.extract(diffs);

        if (candidates.isEmpty()) {
            log.info(
                    "[PatchNote] 문서 업데이트 — 유의미한 변경 후보 없음. sourceId: {}", event.sourceId());
            return;
        }

        PendingItemStatus status =
                event.excludeFromPatchNote()
                        ? PendingItemStatus.EXCLUDED
                        : PendingItemStatus.PENDING;

        String lastTitle = null;
        for (PatchCandidate candidate : candidates) {
            // 3. 후보별 LLM 호출로 플레이어 친화적 요약 생성
            DocumentSummaryResult result =
                    documentChangeGenerator.generate(
                            candidate,
                            event.documentName(),
                            event.documentGroupName(),
                            event.category());

            PatchType patchType =
                    patchTypeResolver.resolveFromLlmCategory(result.categoryFromLlm());
            String choseong = choseongUtil.extract(result.title());

            // 4. 후보별 DTO 조립 (change_index = candidate.chunkIndex, evidence/score 포함)
            PendingItemCreateRequest dto =
                    new PendingItemCreateRequest(
                            event.projectId(),
                            event.sourceId(),
                            SourceType.DOCUMENT,
                            result.title(),
                            result.summary(),
                            choseong,
                            patchType,
                            status,
                            event.sourceCreatedAt(),
                            candidate.chunkIndex(),
                            candidate.evidence(),
                            candidate.score());

            pendingItemUpsertService.upsertPendingItem(dto);
            lastTitle = result.title();

            log.debug(
                    "[PatchNote] 변경 후보 pending_item 적재 - sourceId: {}, chunkIndex: {},"
                            + " patchType: {}, score: {}",
                    event.sourceId(),
                    candidate.chunkIndex(),
                    patchType,
                    candidate.score());
        }

        log.info(
                "[PatchNote] 문서 업데이트 pending_item 적재 완료 - sourceId: {}, candidates: {}",
                event.sourceId(),
                candidates.size());

        if (lastTitle != null) {
            eventPublisher.publishEvent(
                    new DocumentPendingItemCreatedEvent(
                            event.sourceId(), event.projectId(), lastTitle, status));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    private void updateAffectsPlayerBestEffort(Long sourceId, boolean affectsPlayer) {
        try {
            vectorStoreRepository.updateAffectsPlayerBySourceId(
                    sourceId, SourceType.DOCUMENT, affectsPlayer);
        } catch (Exception e) {
            log.warn(
                    "[PatchNote] affects_player 메타데이터 업데이트 실패 (reranking 영향 없음) - sourceId: {}",
                    sourceId,
                    e);
        }
    }
}

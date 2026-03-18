package kr.java.documind.domain.patchnote.event;

import java.util.List;
import kr.java.documind.domain.archive.vector.model.repository.VectorStoreRepository;
import kr.java.documind.domain.patchnote.exception.DocumentEmbeddingEmptyException;
import kr.java.documind.domain.patchnote.infrastructure.DocumentSummaryGenerator;
import kr.java.documind.domain.patchnote.model.dto.DocumentSummaryResult;
import kr.java.documind.domain.patchnote.model.dto.PendingItemCreateDto;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.service.DocumentMeaningfulnessService;
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
 * <h3>처리 흐름</h3>
 *
 * <ol>
 *   <li>벡터 스토어에서 문서 청크 조회 (LLM 입력 + 유의미성 판단용)
 *   <li>유의미성 검증: trivial 변경이면 적재 생략
 *   <li>LLM 요약 생성: title, summary, categoryFromLlm 추출
 *   <li>PatchType 분류: LLM 카테고리 문자열 → PatchType enum
 *   <li>초성 추출 (검색용)
 *   <li>pending_item DB upsert (문서 벡터는 EtlService 담당 — DB 저장만 수행)
 * </ol>
 *
 * <p>LLM 실패 시 {@link DocumentSummaryGenerator} 내부 fallback으로 문서명을 title/summary로 사용한다.
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

    /** 청크 조회 건수 — 문서 앞부분을 대표 텍스트로 사용 */
    private static final int CHUNK_RETRIEVAL_LIMIT = 10;

    private final VectorStoreRepository vectorStoreRepository;
    private final DocumentMeaningfulnessService meaningfulnessService;
    private final DocumentSummaryGenerator documentSummaryGenerator;
    private final PatchTypeResolver patchTypeResolver;
    private final PendingItemUpsertService pendingItemUpsertService;
    private final ChoseongUtil choseongUtil;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 문서 임베딩 완료 시 pending_item을 적재한다.
     *
     * <p>실패 예외는 호출 스택 상위로 전파하여 {@code CustomAsyncExceptionHandler}에 위임한다. 단, trivial 변경(유의미하지 않은
     * 문서)은 예외 없이 조기 반환한다.
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

        // 1. 벡터 스토어에서 문서 청크 조회 (LLM 입력 + 유의미성 판단용)
        List<String> chunks =
                vectorStoreRepository.findContentsBySourceId(
                        event.sourceId(), SourceType.DOCUMENT, CHUNK_RETRIEVAL_LIMIT);

        if (chunks.isEmpty()) {
            // EtlService가 빈 청크는 FAILED 처리하지만, 극히 드문 경우 조회 시점에 청크가 없을 수 있음
            throw new DocumentEmbeddingEmptyException(event.sourceId());
        }

        String currentText = String.join("\n", chunks);

        // 2. 유의미성 검증
        //    previousText는 현재 미지원(null) → 신규/업데이트 모두 true 반환
        //    문서 도메인 팀과 이전 버전 텍스트 전달 협의 완료 시 null 대신 실제 값 전달
        if (!meaningfulnessService.isMeaningful(event.isNewDocument(), currentText, null)) {
            log.info("[PatchNote] 문서 변경 경미 — pending_item 미적재. sourceId: {}", event.sourceId());
            // TODO: 경미 변경 안내 top-toast 알림 발행 (알림 서비스 구현 후 연동)
            return;
        }

        // 3. LLM 요약 생성 (실패 시 DocumentSummaryGenerator 내부 fallback 처리)
        DocumentSummaryResult summaryResult =
                documentSummaryGenerator.generate(
                        event.documentName(), event.documentGroupName(), event.category(), chunks);

        // 4. PatchType 분류 (LLM 응답의 category 문자열 → PatchType enum)
        PatchType patchType = patchTypeResolver.resolveFromLlmCategory(summaryResult.categoryFromLlm());

        // 5. 초성 추출 (검색용)
        String choseong = choseongUtil.extract(summaryResult.title());

        // 6. DTO 조립
        //    excludeFromPatchNote=true이면 EXCLUDED로 적재 (이슈와 동일 전략)
        //    LLM 추출 및 pending_item 저장은 exclude 여부와 관계없이 항상 수행
        PendingItemStatus status =
                event.excludeFromPatchNote()
                        ? PendingItemStatus.EXCLUDED
                        : PendingItemStatus.PENDING;

        PendingItemCreateDto dto =
                new PendingItemCreateDto(
                        event.projectId(),
                        event.sourceId(),
                        SourceType.DOCUMENT,
                        summaryResult.title(),
                        summaryResult.summary(),
                        choseong,
                        patchType,
                        status,
                        event.sourceCreatedAt());

        // 7. DB upsert (문서 벡터는 EtlService가 담당 — 여기서는 pending_item DB 저장만 수행)
        //    @Retryable 3회 적용. 최종 실패 시 PendingItemUpsertFailedException throw
        pendingItemUpsertService.upsertPendingItem(dto);

        // 8. 벡터 메타데이터에 affects_player 기록 (reranking 필터용)
        //    LLM이 isUserFacing=false로 판단한 문서는 유저 향 쿼리 reranking 시 후순위로 처리된다.
        //    upsert 성공 이후 best-effort로 수행하며, 실패 시 경고 로그만 남기고 진행한다.
        try {
            vectorStoreRepository.updateAffectsPlayerBySourceId(
                    event.sourceId(), SourceType.DOCUMENT, summaryResult.affectsPlayer());
        } catch (Exception e) {
            log.warn(
                    "[PatchNote] affects_player 메타데이터 업데이트 실패 (reranking 영향 없음) - sourceId: {}",
                    event.sourceId(),
                    e);
        }

        log.info(
                "[PatchNote] 문서 pending_item 적재 완료 - sourceId: {}, patchType: {}, status: {}",
                event.sourceId(),
                patchType,
                status);

        // 성공 이벤트 발행 — 알림 도메인이 구독하여 alarm-toast + 헤더 배지 전달
        eventPublisher.publishEvent(
                new DocumentPendingItemCreatedEvent(
                        event.sourceId(), event.projectId(), dto.title(), status));
    }
}

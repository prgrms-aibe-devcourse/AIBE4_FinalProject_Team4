package kr.java.documind.domain.patchnote.event;

import java.time.ZoneOffset;
import java.util.List;
import kr.java.documind.domain.archive.vector.model.repository.VectorStoreRepository;
import kr.java.documind.domain.patchnote.infrastructure.DocumentPatchTypeClassifier;
import kr.java.documind.domain.patchnote.infrastructure.DocumentSummaryGenerator;
import kr.java.documind.domain.patchnote.model.dto.DocumentSummaryResult;
import kr.java.documind.domain.patchnote.model.dto.PendingItemCreateDto;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.service.DocumentMeaningfulnessService;
import kr.java.documind.domain.patchnote.service.PendingItemService;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.util.ChoseongUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>LLM 실패 시 {@link DocumentSummaryGenerator} 내부 fallback으로 문서명을 title/summary로 사용한다. pending_item
 * DB upsert 최종 실패 시 {@code PendingItemUpsertFailedException}이 전파되어 {@code
 * CustomAsyncExceptionHandler}가 처리한다.
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
    private final DocumentPatchTypeClassifier patchTypeClassifier;
    private final PendingItemService pendingItemService;
    private final ChoseongUtil choseongUtil;

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
                "[PatchNote] 문서 임베딩 완료 감지 - sourceId: {}, projectId: {}, isNew: {}",
                event.sourceId(),
                event.projectId(),
                event.isNewDocument());

        // 1. 벡터 스토어에서 문서 청크 조회 (LLM 입력 + 유의미성 판단용)
        List<String> chunks =
                vectorStoreRepository.findContentsBySourceId(
                        event.sourceId(), SourceType.DOCUMENT, CHUNK_RETRIEVAL_LIMIT);
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
        PatchType patchType = patchTypeClassifier.classify(summaryResult.categoryFromLlm());

        // 5. 초성 추출 (검색용)
        String choseong = choseongUtil.extract(summaryResult.title());

        // 6. DTO 조립
        PendingItemCreateDto dto =
                new PendingItemCreateDto(
                        event.projectId(),
                        event.sourceId(),
                        SourceType.DOCUMENT,
                        summaryResult.title(),
                        summaryResult.summary(),
                        choseong,
                        patchType,
                        PendingItemStatus.PENDING,
                        event.occurredAt().atOffset(ZoneOffset.UTC));

        // 7. DB upsert (문서 벡터는 EtlService가 담당 — 여기서는 pending_item DB 저장만 수행)
        //    @Retryable 3회 적용. 최종 실패 시 PendingItemUpsertFailedException throw
        pendingItemService.upsertPendingItem(dto);

        log.info(
                "[PatchNote] 문서 pending_item 적재 완료 - sourceId: {}, patchType: {}",
                event.sourceId(),
                patchType);

        // TODO: 성공 알림 이벤트 발행 (alarm-toast + 헤더 배지) — 알림 서비스 구현 후 연동
    }
}

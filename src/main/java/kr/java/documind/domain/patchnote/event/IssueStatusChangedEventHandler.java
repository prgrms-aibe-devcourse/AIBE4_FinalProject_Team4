package kr.java.documind.domain.patchnote.event;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import kr.java.documind.domain.archive.vector.infrastructure.EmbeddingModelClient;
import kr.java.documind.domain.archive.vector.infrastructure.VectorStoreManager;
import kr.java.documind.domain.issue.event.IssueDeletedEvent;
import kr.java.documind.domain.issue.event.IssueStatusChangedEvent;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.entity.IssueComment;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.repository.CommentRepository;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.domain.notification.model.enums.NotificationEventType;
import kr.java.documind.domain.patchnote.exception.IssueInsufficientInfoException;
import kr.java.documind.domain.patchnote.infrastructure.IssueSummaryGenerator;
import kr.java.documind.domain.patchnote.model.dto.IssueChunkingSource;
import kr.java.documind.domain.patchnote.model.dto.IssueCommentChunkSource;
import kr.java.documind.domain.patchnote.model.dto.IssueSummaryResult;
import kr.java.documind.domain.patchnote.model.dto.PendingItemCreateRequest;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.service.IssueChunkingService;
import kr.java.documind.domain.patchnote.service.PendingItemRollbackService;
import kr.java.documind.domain.patchnote.service.PendingItemUpsertService;
import kr.java.documind.domain.patchnote.util.PatchTypeResolver;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.util.ChoseongUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class IssueStatusChangedEventHandler {

    /** 정보 충분성 판단 기준: 텍스트 최소 길이 */
    private static final int MIN_CONTENT_LENGTH = 15;

    private final IssueRepository issueRepository;
    private final CommentRepository commentRepository;
    private final IssueChunkingService issueChunkingService;
    private final EmbeddingModelClient embeddingModelClient;
    private final VectorStoreManager vectorStoreManager;
    private final IssueSummaryGenerator issueSummaryGenerator;
    private final PendingItemUpsertService pendingItemUpsertService;
    private final PendingItemRollbackService pendingItemRollbackService;
    private final PatchTypeResolver patchTypeResolver;
    private final ChoseongUtil choseongUtil;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleIssueResolved(IssueStatusChangedEvent event) {
        // 1. RESOLVED 전환 여부 판정 — 아니면 즉시 종료
        if (event.newStatus() != IssueStatus.RESOLVED) {
            return;
        }

        log.info(
                "[PatchNote] 이슈 RESOLVED 감지 - issueId: {}, projectId: {}",
                event.issueId(),
                event.projectId());

        // 2. 이슈 조회
        Issue issue =
                issueRepository
                        .findById(event.issueId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "이슈를 찾을 수 없습니다. issueId: " + event.issueId()));

        // 3. 댓글 로드 — 정보 충분성 판정 및 청킹에 함께 사용
        List<IssueComment> comments =
                commentRepository.findByIssueIdOrderByCreatedAtAsc(issue.getId());

        // 4. 정보 충분성 검증: description, resolutionNote, 또는 댓글 중 하나라도 MIN_CONTENT_LENGTH 이상
        //    모두 미달 시 IssueInsufficientInfoException throw → 담당자 경고 top-toast
        if (isInsufficient(issue, comments)) {
            log.warn("[PatchNote] 이슈 정보 부족 — pending_item 미적재. issueId: {}", event.issueId());
            throw new IssueInsufficientInfoException(event.issueId());
        }

        // 5. 청킹: 배경/해결/합본/댓글 청크 생성 + 이슈 전용 metadata 주입
        List<IssueCommentChunkSource> commentSources = toCommentChunkSources(comments);
        IssueChunkingSource chunkingSource =
                new IssueChunkingSource(
                        issue.getId(),
                        event.projectId(),
                        issue.getTitle(),
                        issue.getDescription(),
                        issue.getResolutionNote(),
                        issue.getSeverity() != null ? issue.getSeverity().name() : null,
                        issue.getIssueType() != null ? issue.getIssueType().name() : null,
                        issue.getResolvedAt() != null ? issue.getResolvedAt().toInstant() : null,
                        commentSources);
        List<Document> chunks = issueChunkingService.buildChunks(chunkingSource);

        // 6. 임베딩 (EmbeddingModelClient 재사용, 기존 문서 ETL과 동일)
        List<String> texts = chunks.stream().map(Document::getText).toList();
        List<float[]> embeddings = embeddingModelClient.embed(texts);

        // 7. LLM 요약 생성 — title(플레이어 친화적 제목) + summary(해요체 2~3문장) 반환
        IssueSummaryResult summaryResult = issueSummaryGenerator.generate(issue);

        // 8. PatchType 결정 (룰 기반, LLM 미사용)
        PatchType patchType = patchTypeResolver.resolveFromIssueType(issue.getIssueType());

        // 9. 초성 추출 (검색용) — LLM이 변환한 플레이어 친화적 제목 기준
        String choseong = choseongUtil.extract(summaryResult.title());

        // 10. PENDING / EXCLUDED 분기
        PendingItemStatus status =
                event.excludeFromPatchNote()
                        ? PendingItemStatus.EXCLUDED
                        : PendingItemStatus.PENDING;

        // 11. sourceCreatedAt: 이슈 RESOLVED 전환 시점
        OffsetDateTime sourceCreatedAt =
                event.occurredAt() != null
                        ? event.occurredAt().atOffset(ZoneOffset.UTC)
                        : OffsetDateTime.now(ZoneOffset.UTC);

        // 12. DTO 조립 — LLM 결과(title, summary) 사용
        //     ISSUE 항목은 change_index=0 고정, evidence/score는 diff 기반 항목 전용이므로 null
        PendingItemCreateRequest dto =
                new PendingItemCreateRequest(
                        event.projectId(),
                        issue.getId(),
                        SourceType.ISSUE,
                        summaryResult.title(),
                        summaryResult.summary(),
                        choseong,
                        patchType,
                        status,
                        sourceCreatedAt,
                        0,
                        null,
                        null);

        // 13. 벡터 저장 → pending_item upsert (PendingItemUpsertService가 순서 보장 + @Retryable 적용)
        //     실패 예외 전파: PendingItemUpsertFailedException | Exception
        //     → CustomAsyncExceptionHandler가 타입별로 관리자 알림 발행
        pendingItemUpsertService.saveVectorThenUpsert(issue.getId(), chunks, embeddings, dto);

        log.info(
                "[PatchNote] pending_item 적재 완료 - issueId: {}, status: {}, actorId: {}",
                event.issueId(),
                status,
                event.actorId());

        // 성공 이벤트 발행 — 알림 도메인이 구독하여 alarm-toast + 헤더 배지 전달
        eventPublisher.publishEvent(
                new PatchNoteNotificationEvent(
                        event.projectId(),
                        List.of(event.actorId()),
                        event.issueId(),
                        NotificationEventType.PATCHNOTE_ISSUE_GENERATED,
                        dto.title(),
                        "이슈가 해결되어 패치노트 항목이 추가되었습니다.",
                    "/projects/" + event.projectId() + "/patch-note/pending-items",
                        true));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleIssueRollback(IssueStatusChangedEvent event) {

        if (event.oldStatus() != IssueStatus.RESOLVED
                || event.newStatus() == IssueStatus.RESOLVED) {
            return;
        }

        log.info(
                "[PatchNote] 이슈 롤백 감지 - issueId: {}, {} → {}",
                event.issueId(),
                event.oldStatus(),
                event.newStatus());

        try {
            boolean shouldDeleteVectors =
                    pendingItemRollbackService.deleteForRollback(
                            event.projectId(), event.issueId(), SourceType.ISSUE);

            if (shouldDeleteVectors) {
                vectorStoreManager.deleteBySourceId(event.issueId(), SourceType.ISSUE);
                log.info(
                        "[PatchNote] 이슈 롤백 완료 — 벡터 + pending_item 삭제. issueId: {}",
                        event.issueId());
            } else {
                log.info(
                        "[PatchNote] 이슈 롤백 완료 — COMPLETED 항목 sourceDeleted 처리만 수행. issueId: {}",
                        event.issueId());
            }
            // TODO: 유저 알림 발행 (정보 top-toast) — 알림 서비스 구현 후 연동

        } catch (Exception e) {
            log.error("[PatchNote] 이슈 롤백 처리 중 예외 발생 - issueId: {}", event.issueId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleIssueDeleted(IssueDeletedEvent event) {
        log.info(
                "[PatchNote] 이슈 삭제 감지 - issueId: {}, projectId: {}",
                event.issueId(),
                event.projectId());
        try {
            pendingItemRollbackService.markSourceDeleted(
                    event.projectId(), event.issueId(), SourceType.ISSUE);
            log.info(
                    "[PatchNote] 이슈 삭제 완료 — pending_item sourceDeleted 처리. issueId: {}",
                    event.issueId());
        } catch (Exception e) {
            log.error("[PatchNote] 이슈 삭제 처리 중 예외 발생 - issueId: {}", event.issueId(), e);
        }
    }

    /** description, resolutionNote, 댓글 중 MIN_CONTENT_LENGTH 이상인 텍스트가 하나도 없으면 정보 부족으로 판정 */
    private boolean isInsufficient(Issue issue, List<IssueComment> comments) {
        if (hasMeaningfulText(issue.getDescription())) return false;
        if (hasMeaningfulText(issue.getResolutionNote())) return false;
        return comments.stream().noneMatch(c -> hasMeaningfulText(c.getContent()));
    }

    private boolean hasMeaningfulText(String text) {
        return text != null && text.strip().length() >= MIN_CONTENT_LENGTH;
    }

    private List<IssueCommentChunkSource> toCommentChunkSources(List<IssueComment> comments) {
        return comments.stream()
                .map(
                        c ->
                                new IssueCommentChunkSource(
                                        c.getId(),
                                        c.getContent(),
                                        c.getCreatedAt() != null
                                                ? c.getCreatedAt().toInstant()
                                                : null))
                .toList();
    }
}

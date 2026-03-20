package kr.java.documind.domain.patchnote.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.archive.vector.infrastructure.EmbeddingModelClient;
import kr.java.documind.domain.archive.vector.infrastructure.VectorStoreManager;
import kr.java.documind.domain.issue.event.IssueDeletedEvent;
import kr.java.documind.domain.issue.event.IssueStatusChangedEvent;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.entity.IssueComment;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.repository.CommentRepository;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.domain.patchnote.exception.IssueInsufficientInfoException;
import kr.java.documind.domain.patchnote.exception.PendingItemUpsertFailedException;
import kr.java.documind.domain.patchnote.infrastructure.IssueSummaryGenerator;
import kr.java.documind.domain.patchnote.model.dto.IssueChunkingSource;
import kr.java.documind.domain.patchnote.model.dto.IssueSummaryResult;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.service.IssueChunkingService;
import kr.java.documind.domain.patchnote.service.PendingItemRollbackService;
import kr.java.documind.domain.patchnote.service.PendingItemUpsertService;
import kr.java.documind.domain.patchnote.util.PatchTypeResolver;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.util.ChoseongUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueStatusChangedEventHandler 단위 테스트")
class IssueStatusChangedEventHandlerTest {

    @InjectMocks private IssueStatusChangedEventHandler handler;

    @Mock private IssueRepository issueRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private IssueChunkingService issueChunkingService;
    @Mock private EmbeddingModelClient embeddingModelClient;
    @Mock private VectorStoreManager vectorStoreManager;
    @Mock private IssueSummaryGenerator issueSummaryGenerator;
    @Mock private PendingItemUpsertService pendingItemUpsertService;
    @Mock private PendingItemRollbackService pendingItemRollbackService;
    @Mock private PatchTypeResolver patchTypeResolver;
    @Mock private ChoseongUtil choseongUtil;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long ISSUE_ID = 1L;
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    // ──────────────────────────────────────────────────────────────────────────
    // 픽스처 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    private IssueStatusChangedEvent resolvedEvent(boolean excludeFromPatchNote) {
        return new IssueStatusChangedEvent(
                ISSUE_ID,
                PROJECT_ID,
                IssueStatus.IN_PROGRESS,
                IssueStatus.RESOLVED,
                excludeFromPatchNote,
                ACTOR_ID,
                Instant.now());
    }

    private IssueStatusChangedEvent nonResolvedEvent(IssueStatus newStatus) {
        return new IssueStatusChangedEvent(
                ISSUE_ID, PROJECT_ID, IssueStatus.TODO, newStatus, false, ACTOR_ID, Instant.now());
    }

    private IssueStatusChangedEvent rollbackEvent(IssueStatus newStatus) {
        return new IssueStatusChangedEvent(
                ISSUE_ID,
                PROJECT_ID,
                IssueStatus.RESOLVED,
                newStatus,
                false,
                ACTOR_ID,
                Instant.now());
    }

    private Issue sufficientIssue(String description, String resolutionNote) {
        Issue issue =
                Issue.builder()
                        .projectId(PROJECT_ID)
                        .title("테스트 이슈 제목")
                        .description(description)
                        .resolutionNote(resolutionNote)
                        .fingerprint("fp-test")
                        .build();
        ReflectionTestUtils.setField(issue, "id", ISSUE_ID);
        return issue;
    }

    /** 성공 경로 공통 stubbing (댓글 없음) */
    private void stubSuccessPath(Issue issue) {
        given(issueRepository.findById(ISSUE_ID)).willReturn(Optional.of(issue));
        given(commentRepository.findByIssueIdOrderByCreatedAtAsc(ISSUE_ID)).willReturn(List.of());
        given(issueChunkingService.buildChunks(any())).willReturn(List.of(new Document("청크 텍스트")));
        given(embeddingModelClient.embed(any()))
                .willReturn(List.of(new float[] {0.1f, 0.2f, 0.3f}));
        given(issueSummaryGenerator.generate(any()))
                .willReturn(new IssueSummaryResult("플레이어 친화적 제목", "요약입니다"));
        given(patchTypeResolver.resolveFromIssueType(any())).willReturn(PatchType.FIX);
        given(choseongUtil.extract(any())).willReturn("ㅍㄹㅇ");
    }

    /** 발행된 IssuePendingItemCreatedEvent 캡처 */
    private IssuePendingItemCreatedEvent captureCreatedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        return (IssuePendingItemCreatedEvent) captor.getValue();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleIssueResolved() — 조기 종료
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleIssueResolved() - 조기 종료 및 이슈 조회")
    class HandleIssueResolvedEarlyReturn {

        @Test
        @DisplayName("핸들러 조기 종료: newStatus=IN_PROGRESS → issueRepository 호출 없음")
        void handleIssueResolved_RESOLVED아닌전환_즉시종료() {
            // Given
            IssueStatusChangedEvent event = nonResolvedEvent(IssueStatus.IN_PROGRESS);

            // When
            handler.handleIssueResolved(event);

            // Then
            then(issueRepository).should(never()).findById(any());
        }

        @Test
        @DisplayName("핸들러 조기 종료: newStatus=RESOLVED이지만 이슈 없음 → IllegalStateException")
        void handleIssueResolved_이슈없음_IllegalStateException발생() {
            // Given
            IssueStatusChangedEvent event = resolvedEvent(false);
            given(issueRepository.findById(ISSUE_ID)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> handler.handleIssueResolved(event))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(String.valueOf(ISSUE_ID));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleIssueResolved() — 정보 충분성 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleIssueResolved() - 정보 충분성 검증")
    class HandleIssueResolvedSufficiency {

        @Test
        @DisplayName("정보 검증: description, resolutionNote 모두 짧음 → IssueInsufficientInfoException")
        void
                handleIssueResolved_description과resolutionNote모두14자이하_IssueInsufficientInfoException발생() {
            // Given — 10자 (MIN_CONTENT_LENGTH=15 미달)
            Issue issue = sufficientIssue("짧은설명1234567", null);
            given(issueRepository.findById(ISSUE_ID)).willReturn(Optional.of(issue));
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When & Then
            assertThatThrownBy(() -> handler.handleIssueResolved(event))
                    .isInstanceOf(IssueInsufficientInfoException.class);
        }

        @Test
        @DisplayName("정보 검증: description 정확히 14자(경계값-1) → IssueInsufficientInfoException")
        void
                handleIssueResolved_description14자_resolutionNote없음_IssueInsufficientInfoException발생() {
            // Given — 정확히 14자
            Issue issue = sufficientIssue("A".repeat(14), null);
            given(issueRepository.findById(ISSUE_ID)).willReturn(Optional.of(issue));
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When & Then
            assertThatThrownBy(() -> handler.handleIssueResolved(event))
                    .isInstanceOf(IssueInsufficientInfoException.class);
        }

        @Test
        @DisplayName("정보 검증: description 정확히 15자(경계값) → 정보 충분으로 진행")
        void handleIssueResolved_description15자_정보충분_진행() {
            // Given — 정확히 15자
            Issue issue = sufficientIssue("A".repeat(15), null);
            stubSuccessPath(issue);
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When
            handler.handleIssueResolved(event);

            // Then
            then(pendingItemUpsertService)
                    .should()
                    .saveVectorThenUpsert(any(), any(), any(), any());
        }

        @Test
        @DisplayName("정보 검증: resolutionNote 15자 이상, description=null → 정보 충분으로 진행")
        void handleIssueResolved_resolutionNote15자이상_정보충분_진행() {
            // Given
            Issue issue = sufficientIssue(null, "A".repeat(15));
            stubSuccessPath(issue);
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When
            handler.handleIssueResolved(event);

            // Then
            then(pendingItemUpsertService)
                    .should()
                    .saveVectorThenUpsert(any(), any(), any(), any());
        }

        @Test
        @DisplayName("정보 검증: 공백만 있는 description(strip 후 0자) → IssueInsufficientInfoException")
        void handleIssueResolved_공백만있는description_부족처리() {
            // Given — 공백 15자, strip 후 0자
            Issue issue = sufficientIssue("               ", null);
            given(issueRepository.findById(ISSUE_ID)).willReturn(Optional.of(issue));
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When & Then
            assertThatThrownBy(() -> handler.handleIssueResolved(event))
                    .isInstanceOf(IssueInsufficientInfoException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleIssueResolved() — 성공 흐름
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleIssueResolved() - 성공 흐름")
    class HandleIssueResolvedSuccess {

        @Test
        @DisplayName("성공 흐름: excludeFromPatchNote=false → 이벤트 status=PENDING")
        void handleIssueResolved_excludeFromPatchNote_false_PENDING상태로생성() {
            // Given
            Issue issue = sufficientIssue("충분한 설명: 이슈 내용입니다", null);
            stubSuccessPath(issue);
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When
            handler.handleIssueResolved(event);

            // Then
            IssuePendingItemCreatedEvent published = captureCreatedEvent();
            assertThat(published.status()).isEqualTo(PendingItemStatus.PENDING);
        }

        @Test
        @DisplayName("성공 흐름: excludeFromPatchNote=true → 이벤트 status=EXCLUDED")
        void handleIssueResolved_excludeFromPatchNote_true_EXCLUDED상태로생성() {
            // Given
            Issue issue = sufficientIssue("충분한 설명: 이슈 내용입니다", null);
            stubSuccessPath(issue);
            IssueStatusChangedEvent event = resolvedEvent(true);

            // When
            handler.handleIssueResolved(event);

            // Then
            IssuePendingItemCreatedEvent published = captureCreatedEvent();
            assertThat(published.status()).isEqualTo(PendingItemStatus.EXCLUDED);
        }

        @Test
        @DisplayName("성공 흐름: saveVectorThenUpsert 완료 후 IssuePendingItemCreatedEvent 1회 발행")
        void handleIssueResolved_성공시_IssuePendingItemCreatedEvent발행() {
            // Given
            Issue issue = sufficientIssue("충분한 설명: 이슈 내용입니다", null);
            stubSuccessPath(issue);
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When
            handler.handleIssueResolved(event);

            // Then — IssuePendingItemCreatedEvent에는 issueId, projectId가 올바르게 세팅
            IssuePendingItemCreatedEvent published = captureCreatedEvent();
            assertThat(published.issueId()).isEqualTo(ISSUE_ID);
            assertThat(published.projectId()).isEqualTo(PROJECT_ID);
        }

        @Test
        @DisplayName("성공 흐름: excludeFromPatchNote=true 이어도 saveVectorThenUpsert 호출됨")
        void handleIssueResolved_excludeFromPatchNote_true_saveVectorThenUpsert호출됨() {
            // Given
            Issue issue = sufficientIssue("충분한 설명: 이슈 내용입니다", null);
            stubSuccessPath(issue);
            IssueStatusChangedEvent event = resolvedEvent(true);

            // When
            handler.handleIssueResolved(event);

            // Then
            then(pendingItemUpsertService)
                    .should()
                    .saveVectorThenUpsert(any(), any(), any(), any());
        }

        @Test
        @DisplayName("성공 흐름: occurredAt=null → NPE 없이 현재 시각으로 대체")
        void handleIssueResolved_occurredAt_null_정상처리() {
            // Given
            IssueStatusChangedEvent event =
                    new IssueStatusChangedEvent(
                            ISSUE_ID,
                            PROJECT_ID,
                            IssueStatus.IN_PROGRESS,
                            IssueStatus.RESOLVED,
                            false,
                            ACTOR_ID,
                            null);
            Issue issue = sufficientIssue("충분한 설명: 이슈 내용입니다", null);
            stubSuccessPath(issue);

            // When (NPE 없이 완료)
            handler.handleIssueResolved(event);

            // Then
            then(pendingItemUpsertService)
                    .should()
                    .saveVectorThenUpsert(any(), any(), any(), any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleIssueResolved() — 처리 순서 보장
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleIssueResolved() - 처리 순서 보장")
    class HandleIssueResolvedOrder {

        @Test
        @DisplayName("순서 보장: embed → saveVectorThenUpsert → publishEvent 순서로 호출")
        void handleIssueResolved_InOrder_embed_save_publish() {
            // Given
            Issue issue = sufficientIssue("충분한 설명: 이슈 내용입니다", null);
            stubSuccessPath(issue);
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When
            handler.handleIssueResolved(event);

            // Then
            InOrder order = inOrder(embeddingModelClient, pendingItemUpsertService, eventPublisher);
            then(embeddingModelClient).should(order).embed(any());
            then(pendingItemUpsertService)
                    .should(order)
                    .saveVectorThenUpsert(any(), any(), any(), any());
            then(eventPublisher).should(order).publishEvent(any(Object.class));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleIssueResolved() — 실패 시 예외 전파
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleIssueResolved() - 실패 시 예외 전파")
    class HandleIssueResolvedFailure {

        @Test
        @DisplayName("실패 전파: saveVectorThenUpsert RuntimeException → 예외 전파, 성공 이벤트 미발행")
        void handleIssueResolved_벡터저장실패시_RuntimeException전파_성공이벤트미발행() {
            // Given
            Issue issue = sufficientIssue("충분한 설명: 이슈 내용입니다", null);
            stubSuccessPath(issue);
            willThrow(new RuntimeException("벡터 저장 실패"))
                    .given(pendingItemUpsertService)
                    .saveVectorThenUpsert(any(), any(), any(), any());
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When & Then
            assertThatThrownBy(() -> handler.handleIssueResolved(event))
                    .isInstanceOf(RuntimeException.class);
            then(eventPublisher).should(never()).publishEvent(any());
        }

        @Test
        @DisplayName("실패 전파: PendingItemUpsertFailedException → 그대로 전파")
        void handleIssueResolved_PendingItemUpsertFailedException_전파() {
            // Given
            Issue issue = sufficientIssue("충분한 설명: 이슈 내용입니다", null);
            stubSuccessPath(issue);
            willThrow(new PendingItemUpsertFailedException(ISSUE_ID))
                    .given(pendingItemUpsertService)
                    .saveVectorThenUpsert(any(), any(), any(), any());
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When & Then
            assertThatThrownBy(() -> handler.handleIssueResolved(event))
                    .isInstanceOf(PendingItemUpsertFailedException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleIssueRollback()
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleIssueRollback()")
    class HandleIssueRollback {

        @Test
        @DisplayName("롤백 조기 종료: oldStatus=IN_PROGRESS → deleteForRollback 미호출")
        void handleIssueRollback_oldStatus가RESOLVED아님_즉시종료() {
            // Given
            IssueStatusChangedEvent event =
                    new IssueStatusChangedEvent(
                            ISSUE_ID,
                            PROJECT_ID,
                            IssueStatus.IN_PROGRESS,
                            IssueStatus.TODO,
                            false,
                            ACTOR_ID,
                            Instant.now());

            // When
            handler.handleIssueRollback(event);

            // Then
            then(pendingItemRollbackService).should(never()).deleteForRollback(any(), any(), any());
        }

        @Test
        @DisplayName("롤백 조기 종료: oldStatus=RESOLVED, newStatus=RESOLVED → 즉시 종료")
        void handleIssueRollback_newStatus가RESOLVED_즉시종료() {
            // Given — RESOLVED → RESOLVED
            IssueStatusChangedEvent event =
                    new IssueStatusChangedEvent(
                            ISSUE_ID,
                            PROJECT_ID,
                            IssueStatus.RESOLVED,
                            IssueStatus.RESOLVED,
                            false,
                            ACTOR_ID,
                            Instant.now());

            // When
            handler.handleIssueRollback(event);

            // Then
            then(pendingItemRollbackService).should(never()).deleteForRollback(any(), any(), any());
        }

        @Test
        @DisplayName("롤백 처리: deleteForRollback=true → vectorStoreManager.deleteBySourceId 호출")
        void handleIssueRollback_deleteForRollback_true반환시_벡터삭제호출() {
            // Given
            IssueStatusChangedEvent event = rollbackEvent(IssueStatus.IN_PROGRESS);
            given(
                            pendingItemRollbackService.deleteForRollback(
                                    PROJECT_ID, ISSUE_ID, SourceType.ISSUE))
                    .willReturn(true);

            // When
            handler.handleIssueRollback(event);

            // Then
            then(vectorStoreManager).should().deleteBySourceId(ISSUE_ID, SourceType.ISSUE);
        }

        @Test
        @DisplayName("롤백 처리: deleteForRollback=false → vectorStoreManager.deleteBySourceId 미호출")
        void handleIssueRollback_deleteForRollback_false반환시_벡터삭제미호출() {
            // Given
            IssueStatusChangedEvent event = rollbackEvent(IssueStatus.TODO);
            given(
                            pendingItemRollbackService.deleteForRollback(
                                    PROJECT_ID, ISSUE_ID, SourceType.ISSUE))
                    .willReturn(false);

            // When
            handler.handleIssueRollback(event);

            // Then
            then(vectorStoreManager).should(never()).deleteBySourceId(any(), any());
        }

        @Test
        @DisplayName("롤백 처리: vectorStoreManager.deleteBySourceId 예외 발생 → 전파되지 않음")
        void handleIssueRollback_벡터삭제예외발생시_삼킴() {
            // Given
            IssueStatusChangedEvent event = rollbackEvent(IssueStatus.IN_PROGRESS);
            given(
                            pendingItemRollbackService.deleteForRollback(
                                    PROJECT_ID, ISSUE_ID, SourceType.ISSUE))
                    .willReturn(true);
            willThrow(new RuntimeException("벡터 삭제 실패"))
                    .given(vectorStoreManager)
                    .deleteBySourceId(ISSUE_ID, SourceType.ISSUE);

            // When & Then (예외 전파 없음)
            handler.handleIssueRollback(event);
        }

        @Test
        @DisplayName("롤백 예외 삼킴: deleteForRollback 예외 발생 → 전파되지 않음")
        void handleIssueRollback_예외발생시_삼킴() {
            // Given
            IssueStatusChangedEvent event = rollbackEvent(IssueStatus.IN_PROGRESS);
            willThrow(new RuntimeException("DB 오류"))
                    .given(pendingItemRollbackService)
                    .deleteForRollback(PROJECT_ID, ISSUE_ID, SourceType.ISSUE);

            // When & Then (예외 전파 없음)
            handler.handleIssueRollback(event);
        }

        @Test
        @DisplayName("롤백 처리: pending_item 없음(false 반환) → 벡터 삭제 미호출")
        void handleIssueRollback_pendingItem없음_벡터삭제미호출() {
            // Given
            IssueStatusChangedEvent event = rollbackEvent(IssueStatus.TODO);
            given(
                            pendingItemRollbackService.deleteForRollback(
                                    PROJECT_ID, ISSUE_ID, SourceType.ISSUE))
                    .willReturn(false);

            // When
            handler.handleIssueRollback(event);

            // Then
            then(vectorStoreManager).should(never()).deleteBySourceId(any(), any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleIssueDeleted()
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleIssueDeleted()")
    class HandleIssueDeleted {

        @Test
        @DisplayName("이슈 삭제: markSourceDeleted 정확한 인자로 호출")
        void handleIssueDeleted_markSourceDeleted_호출() {
            // Given
            IssueDeletedEvent event =
                    new IssueDeletedEvent(ISSUE_ID, PROJECT_ID, ACTOR_ID, Instant.now());

            // When
            handler.handleIssueDeleted(event);

            // Then
            then(pendingItemRollbackService)
                    .should()
                    .markSourceDeleted(PROJECT_ID, ISSUE_ID, SourceType.ISSUE);
        }

        @Test
        @DisplayName("이슈 삭제 예외 삼킴: markSourceDeleted 예외 발생 → 전파되지 않음")
        void handleIssueDeleted_예외발생시_삼킴() {
            // Given
            IssueDeletedEvent event =
                    new IssueDeletedEvent(ISSUE_ID, PROJECT_ID, ACTOR_ID, Instant.now());
            willThrow(new RuntimeException("DB 오류"))
                    .given(pendingItemRollbackService)
                    .markSourceDeleted(PROJECT_ID, ISSUE_ID, SourceType.ISSUE);

            // When & Then (예외 전파 없음)
            handler.handleIssueDeleted(event);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 프로젝트 격리 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("프로젝트 격리")
    class ProjectIsolation {

        @Test
        @DisplayName("프로젝트 격리: 이벤트의 projectId가 성공 이벤트에 그대로 전달")
        void handleIssueResolved_다른프로젝트ID_격리처리() {
            // Given
            UUID otherProjectId = UUID.randomUUID();
            IssueStatusChangedEvent event =
                    new IssueStatusChangedEvent(
                            ISSUE_ID,
                            otherProjectId,
                            IssueStatus.IN_PROGRESS,
                            IssueStatus.RESOLVED,
                            false,
                            ACTOR_ID,
                            Instant.now());
            Issue issue =
                    Issue.builder()
                            .projectId(otherProjectId)
                            .title("다른 프로젝트 이슈")
                            .description("충분한 설명: 이슈 내용입니다")
                            .fingerprint("fp-other")
                            .build();
            ReflectionTestUtils.setField(issue, "id", ISSUE_ID);

            given(issueRepository.findById(ISSUE_ID)).willReturn(Optional.of(issue));
            given(issueChunkingService.buildChunks(any())).willReturn(List.of(new Document("텍스트")));
            given(embeddingModelClient.embed(any())).willReturn(List.of(new float[] {0.1f}));
            given(issueSummaryGenerator.generate(any()))
                    .willReturn(new IssueSummaryResult("제목", "요약"));
            given(patchTypeResolver.resolveFromIssueType(any())).willReturn(PatchType.FIX);
            given(choseongUtil.extract(any())).willReturn("ㅈ");

            // When
            handler.handleIssueResolved(event);

            // Then
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            IssuePendingItemCreatedEvent published =
                    (IssuePendingItemCreatedEvent) captor.getValue();
            assertThat(published.projectId()).isEqualTo(otherProjectId);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 중복 이벤트 멱등성 (idempotency)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("중복 이벤트 처리 (멱등성)")
    class Idempotency {

        @Test
        @DisplayName("중복 이벤트: 동일 이슈 RESOLVED 이벤트 2회 수신 → saveVectorThenUpsert 2회 호출")
        void handleIssueResolved_중복이벤트_saveVectorThenUpsert2회호출() {
            // Given
            Issue issue = sufficientIssue("충분한 설명: 이슈 내용입니다", null);
            stubSuccessPath(issue);
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When
            handler.handleIssueResolved(event);
            handler.handleIssueResolved(event);

            // Then — upsert 내부에서 기존 항목 refresh 처리
            then(pendingItemUpsertService)
                    .should(times(2))
                    .saveVectorThenUpsert(any(), any(), any(), any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 이슈 댓글 관련 테스트
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleIssueResolved() - 이슈 댓글 연동")
    class HandleIssueResolvedComment {

        @Test
        @DisplayName("정보 충분성: description=null, resolutionNote=null, comment>=15자 → 정보 충분으로 진행")
        void handleIssueResolved_comment만있는이슈_정보충분_진행() {
            // Given — description, resolutionNote 없고 comment만 있는 경우
            Issue issue = sufficientIssue(null, null);
            IssueComment comment =
                    IssueComment.create(ISSUE_ID, UUID.randomUUID(), "A".repeat(15), List.of());
            ReflectionTestUtils.setField(comment, "id", 100L);

            given(issueRepository.findById(ISSUE_ID)).willReturn(Optional.of(issue));
            given(commentRepository.findByIssueIdOrderByCreatedAtAsc(ISSUE_ID))
                    .willReturn(List.of(comment));
            given(issueChunkingService.buildChunks(any()))
                    .willReturn(List.of(new Document("청크 텍스트")));
            given(embeddingModelClient.embed(any()))
                    .willReturn(List.of(new float[] {0.1f, 0.2f, 0.3f}));
            given(issueSummaryGenerator.generate(any()))
                    .willReturn(new IssueSummaryResult("플레이어 친화적 제목", "요약입니다"));
            given(patchTypeResolver.resolveFromIssueType(any())).willReturn(PatchType.FIX);
            given(choseongUtil.extract(any())).willReturn("ㅍㄹㅇ");
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When
            handler.handleIssueResolved(event);

            // Then — isInsufficient = false (댓글 ≥ 15자) → saveVectorThenUpsert 호출
            then(pendingItemUpsertService)
                    .should()
                    .saveVectorThenUpsert(any(), any(), any(), any());
        }

        @Test
        @DisplayName(
                "정보 충분성: description=null, resolutionNote=null, comment 14자 → IssueInsufficientInfoException")
        void handleIssueResolved_comment14자이하_정보부족_예외발생() {
            // Given — 댓글이 있어도 14자 이하이면 부족 처리
            Issue issue = sufficientIssue(null, null);
            IssueComment shortComment =
                    IssueComment.create(ISSUE_ID, UUID.randomUUID(), "A".repeat(14), List.of());
            ReflectionTestUtils.setField(shortComment, "id", 101L);

            given(issueRepository.findById(ISSUE_ID)).willReturn(Optional.of(issue));
            given(commentRepository.findByIssueIdOrderByCreatedAtAsc(ISSUE_ID))
                    .willReturn(List.of(shortComment));
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When & Then
            assertThatThrownBy(() -> handler.handleIssueResolved(event))
                    .isInstanceOf(IssueInsufficientInfoException.class);
        }

        @Test
        @DisplayName("청크 생성: 댓글 포함 시 buildChunks에 전달되는 IssueChunkingSource에 comments가 포함됨")
        void handleIssueResolved_comment청크_metadata검증() {
            // Given — description 있는 이슈 + 댓글 1개
            //   IssueChunkDocumentBuilder는 comment 청크 draft에 isResolutionLike()=false를 설정하여
            //   chunk_contains_resolution=false 메타데이터를 부여함 (IssueChunkDraft.isResolutionLike 참조)
            Issue issue = sufficientIssue("충분한 설명: 이슈 내용입니다", null);
            String commentContent = "댓글로 남긴 해결 참고 내용입니다";
            IssueComment comment =
                    IssueComment.create(ISSUE_ID, UUID.randomUUID(), commentContent, List.of());
            ReflectionTestUtils.setField(comment, "id", 200L);

            given(issueRepository.findById(ISSUE_ID)).willReturn(Optional.of(issue));
            given(commentRepository.findByIssueIdOrderByCreatedAtAsc(ISSUE_ID))
                    .willReturn(List.of(comment));
            given(issueChunkingService.buildChunks(any()))
                    .willReturn(List.of(new Document("청크 텍스트")));
            given(embeddingModelClient.embed(any()))
                    .willReturn(List.of(new float[] {0.1f, 0.2f, 0.3f}));
            given(issueSummaryGenerator.generate(any()))
                    .willReturn(new IssueSummaryResult("플레이어 친화적 제목", "요약입니다"));
            given(patchTypeResolver.resolveFromIssueType(any())).willReturn(PatchType.FIX);
            given(choseongUtil.extract(any())).willReturn("ㅍㄹㅇ");
            IssueStatusChangedEvent event = resolvedEvent(false);

            // When
            handler.handleIssueResolved(event);

            // Then — buildChunks에 전달된 IssueChunkingSource의 comments 필드 검증
            ArgumentCaptor<IssueChunkingSource> captor =
                    ArgumentCaptor.forClass(IssueChunkingSource.class);
            then(issueChunkingService).should().buildChunks(captor.capture());
            IssueChunkingSource capturedSource = captor.getValue();

            assertThat(capturedSource.comments()).hasSize(1);
            assertThat(capturedSource.comments().get(0).commentId()).isEqualTo(200L);
            assertThat(capturedSource.comments().get(0).content()).isEqualTo(commentContent);
        }
    }
}

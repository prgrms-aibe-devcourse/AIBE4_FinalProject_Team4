package kr.java.documind.domain.patchnote.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.vector.model.repository.VectorStoreRepository;
import kr.java.documind.domain.patchnote.exception.DocumentEmbeddingEmptyException;
import kr.java.documind.domain.patchnote.exception.PendingItemUpsertFailedException;
import kr.java.documind.domain.patchnote.infrastructure.DocumentSummaryGenerator;
import kr.java.documind.domain.patchnote.model.dto.DocumentSummaryResult;
import kr.java.documind.domain.patchnote.model.dto.PendingItemCreateRequest;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.service.DocumentMeaningfulnessService;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentVectorStatusChangedEventListener 단위 테스트")
class DocumentVectorStatusChangedEventListenerTest {

    @InjectMocks private DocumentVectorStatusChangedEventListener listener;

    @Mock private VectorStoreRepository vectorStoreRepository;
    @Mock private DocumentMeaningfulnessService meaningfulnessService;
    @Mock private DocumentSummaryGenerator documentSummaryGenerator;
    @Mock private PatchTypeResolver patchTypeResolver;
    @Mock private PendingItemUpsertService pendingItemUpsertService;
    @Mock private ChoseongUtil choseongUtil;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long SOURCE_ID = 1L;
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final OffsetDateTime SOURCE_CREATED_AT = OffsetDateTime.now(ZoneOffset.UTC);

    // ──────────────────────────────────────────────────────────────────────────
    // 픽스처 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    private DocumentEmbeddedEvent buildEvent(boolean isNewDocument, boolean excludeFromPatchNote) {
        return new DocumentEmbeddedEvent(
                SOURCE_ID,
                PROJECT_ID,
                1L,
                "design-doc.pdf",
                "기획서 그룹",
                "CHANGE",
                isNewDocument,
                excludeFromPatchNote,
                SOURCE_CREATED_AT);
    }

    /** 청크 조회 → 유의미성 → LLM → PatchType → 초성까지 전 구간 공통 stub */
    private void stubSuccessPath(List<String> chunks, DocumentSummaryResult summaryResult) {
        given(vectorStoreRepository.findContentsBySourceId(SOURCE_ID, SourceType.DOCUMENT, 10))
                .willReturn(chunks);
        given(meaningfulnessService.isMeaningful(any(Boolean.class), anyString(), any()))
                .willReturn(true);
        given(documentSummaryGenerator.generate(anyString(), anyString(), anyString(), anyList()))
                .willReturn(summaryResult);
        given(patchTypeResolver.resolveFromLlmCategory(summaryResult.categoryFromLlm()))
                .willReturn(PatchType.CHANGE);
        given(choseongUtil.extract(summaryResult.title())).willReturn("ㄱㅎㅅ");
    }

    private void stubSuccessPath() {
        stubSuccessPath(
                List.of("청크1", "청크2"),
                new DocumentSummaryResult("기획서 제목", "기획서 요약", "CHANGE", true));
    }

    /** 발행된 DocumentPendingItemCreatedEvent 캡처 */
    private DocumentPendingItemCreatedEvent captureCreatedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        return (DocumentPendingItemCreatedEvent) captor.getValue();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handle() — 청크 없음 예외
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handle() - 벡터 청크 없음")
    class HandleEmptyChunks {

        @Test
        @DisplayName("청크 없음: 빈 리스트 반환 → DocumentEmbeddingEmptyException 발생")
        void handle_청크없음_DocumentEmbeddingEmptyException발생() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            given(vectorStoreRepository.findContentsBySourceId(SOURCE_ID, SourceType.DOCUMENT, 10))
                    .willReturn(List.of());

            // When & Then
            assertThatThrownBy(() -> listener.handle(event))
                    .isInstanceOf(DocumentEmbeddingEmptyException.class)
                    .hasMessageContaining(String.valueOf(SOURCE_ID));
        }

        @Test
        @DisplayName("청크 없음: 성공 이벤트 미발행")
        void handle_청크없음_성공이벤트미발행() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            given(vectorStoreRepository.findContentsBySourceId(SOURCE_ID, SourceType.DOCUMENT, 10))
                    .willReturn(List.of());

            // When & Then
            assertThatThrownBy(() -> listener.handle(event))
                    .isInstanceOf(DocumentEmbeddingEmptyException.class);
            then(eventPublisher).should(never()).publishEvent(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handle() — 유의미성 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handle() - 유의미성 검증")
    class HandleMeaningfulness {

        @Test
        @DisplayName("유의미하지 않은 변경: isMeaningful=false → pending_item 미적재 조기 반환")
        void handle_유의미하지않은변경_pending_item미적재() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(false, false);
            given(vectorStoreRepository.findContentsBySourceId(SOURCE_ID, SourceType.DOCUMENT, 10))
                    .willReturn(List.of("청크1"));
            given(meaningfulnessService.isMeaningful(any(Boolean.class), anyString(), any()))
                    .willReturn(false);

            // When
            listener.handle(event);

            // Then
            then(pendingItemUpsertService).should(never()).upsertPendingItem(any());
            then(eventPublisher).should(never()).publishEvent(any());
        }

        @Test
        @DisplayName("유의미성 검증: isNewDocument=true → meaningfulnessService에 true 전달")
        void handle_isNewDocument_true_meaningfulnessService에true전달() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            stubSuccessPath();

            // When
            listener.handle(event);

            // Then
            ArgumentCaptor<Boolean> isNewCaptor = ArgumentCaptor.forClass(Boolean.class);
            then(meaningfulnessService)
                    .should()
                    .isMeaningful(isNewCaptor.capture(), anyString(), any());
            assertThat(isNewCaptor.getValue()).isTrue();
        }

        @Test
        @DisplayName("유의미성 검증: isNewDocument=false → meaningfulnessService에 false 전달")
        void handle_isNewDocument_false_meaningfulnessService에false전달() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(false, false);
            stubSuccessPath();

            // When
            listener.handle(event);

            // Then
            ArgumentCaptor<Boolean> isNewCaptor = ArgumentCaptor.forClass(Boolean.class);
            then(meaningfulnessService)
                    .should()
                    .isMeaningful(isNewCaptor.capture(), anyString(), any());
            assertThat(isNewCaptor.getValue()).isFalse();
        }

        @Test
        @DisplayName("유의미성 검증: 청크 join 결과(currentText)가 meaningfulnessService 두 번째 인자로 전달")
        void handle_청크join_currentText로전달() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            given(vectorStoreRepository.findContentsBySourceId(SOURCE_ID, SourceType.DOCUMENT, 10))
                    .willReturn(List.of("청크A", "청크B", "청크C"));
            given(meaningfulnessService.isMeaningful(any(Boolean.class), anyString(), any()))
                    .willReturn(false); // 조기 반환 — currentText만 캡처

            // When
            listener.handle(event);

            // Then — 청크를 "\n"으로 조인한 값이 전달되어야 함
            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            then(meaningfulnessService)
                    .should()
                    .isMeaningful(any(Boolean.class), textCaptor.capture(), any());
            assertThat(textCaptor.getValue()).isEqualTo("청크A\n청크B\n청크C");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handle() — PendingItemStatus (excludeFromPatchNote 정책)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handle() - PendingItemStatus 정책")
    class HandlePendingItemStatus {

        @Test
        @DisplayName("excludeFromPatchNote=false → dto.status=PENDING")
        void handle_excludeFromPatchNote_false_dto상태PENDING() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            stubSuccessPath();

            // When
            listener.handle(event);

            // Then
            ArgumentCaptor<PendingItemCreateRequest> dtoCaptor =
                    ArgumentCaptor.forClass(PendingItemCreateRequest.class);
            then(pendingItemUpsertService).should().upsertPendingItem(dtoCaptor.capture());
            assertThat(dtoCaptor.getValue().status()).isEqualTo(PendingItemStatus.PENDING);
        }

        @Test
        @DisplayName("excludeFromPatchNote=true → dto.status=EXCLUDED")
        void handle_excludeFromPatchNote_true_dto상태EXCLUDED() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, true);
            stubSuccessPath();

            // When
            listener.handle(event);

            // Then
            ArgumentCaptor<PendingItemCreateRequest> dtoCaptor =
                    ArgumentCaptor.forClass(PendingItemCreateRequest.class);
            then(pendingItemUpsertService).should().upsertPendingItem(dtoCaptor.capture());
            assertThat(dtoCaptor.getValue().status()).isEqualTo(PendingItemStatus.EXCLUDED);
        }

        @Test
        @DisplayName("excludeFromPatchNote=true → 발행된 이벤트 status=EXCLUDED")
        void handle_excludeFromPatchNote_true_이벤트status_EXCLUDED() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, true);
            stubSuccessPath();

            // When
            listener.handle(event);

            // Then
            DocumentPendingItemCreatedEvent published = captureCreatedEvent();
            assertThat(published.status()).isEqualTo(PendingItemStatus.EXCLUDED);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handle() — DTO 조립 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handle() - PendingItemCreateDto 조립")
    class HandleDtoAssembly {

        @Test
        @DisplayName("dto 조립: sourceType=DOCUMENT, projectId/sourceId 이벤트값 일치")
        void handle_dto조립_sourceType_projectId_sourceId검증() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            stubSuccessPath();

            // When
            listener.handle(event);

            // Then
            ArgumentCaptor<PendingItemCreateRequest> dtoCaptor =
                    ArgumentCaptor.forClass(PendingItemCreateRequest.class);
            then(pendingItemUpsertService).should().upsertPendingItem(dtoCaptor.capture());
            PendingItemCreateRequest dto = dtoCaptor.getValue();
            assertThat(dto.sourceType()).isEqualTo(SourceType.DOCUMENT);
            assertThat(dto.projectId()).isEqualTo(PROJECT_ID);
            assertThat(dto.sourceId()).isEqualTo(SOURCE_ID);
        }

        @Test
        @DisplayName("dto 조립: LLM 카테고리 → PatchTypeResolver.resolveFromLlmCategory 호출")
        void handle_dto조립_patchTypeResolver호출() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            DocumentSummaryResult summary = new DocumentSummaryResult("제목", "요약", "NEW", true);
            stubSuccessPath(List.of("청크1"), summary);
            given(patchTypeResolver.resolveFromLlmCategory("NEW")).willReturn(PatchType.NEW);
            given(choseongUtil.extract("제목")).willReturn("ㅈㅁ");

            // When
            listener.handle(event);

            // Then
            then(patchTypeResolver).should().resolveFromLlmCategory("NEW");
        }

        @Test
        @DisplayName("dto 조립: choseongUtil.extract(title) → dto.choseong에 반영")
        void handle_dto조립_choseong추출검증() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            DocumentSummaryResult summary =
                    new DocumentSummaryResult("업데이트 노트", "요약", "CHANGE", true);
            given(vectorStoreRepository.findContentsBySourceId(SOURCE_ID, SourceType.DOCUMENT, 10))
                    .willReturn(List.of("청크1"));
            given(meaningfulnessService.isMeaningful(any(Boolean.class), anyString(), any()))
                    .willReturn(true);
            given(
                            documentSummaryGenerator.generate(
                                    anyString(), anyString(), anyString(), anyList()))
                    .willReturn(summary);
            given(patchTypeResolver.resolveFromLlmCategory("CHANGE")).willReturn(PatchType.CHANGE);
            given(choseongUtil.extract("업데이트 노트")).willReturn("ㅇㄷㅇㅌ ㄴㅌ");

            // When
            listener.handle(event);

            // Then
            ArgumentCaptor<PendingItemCreateRequest> dtoCaptor =
                    ArgumentCaptor.forClass(PendingItemCreateRequest.class);
            then(pendingItemUpsertService).should().upsertPendingItem(dtoCaptor.capture());
            assertThat(dtoCaptor.getValue().choseong()).isEqualTo("ㅇㄷㅇㅌ ㄴㅌ");
        }

        @Test
        @DisplayName("dto 조립: dto.title() · dto.summary() 가 LLM 결과값과 일치")
        void handle_dto조립_title_summary_LLM결과반영() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            DocumentSummaryResult summary =
                    new DocumentSummaryResult("LLM제목", "LLM요약내용", "CHANGE", true);
            given(vectorStoreRepository.findContentsBySourceId(SOURCE_ID, SourceType.DOCUMENT, 10))
                    .willReturn(List.of("청크1"));
            given(meaningfulnessService.isMeaningful(any(Boolean.class), anyString(), any()))
                    .willReturn(true);
            given(
                            documentSummaryGenerator.generate(
                                    anyString(), anyString(), anyString(), anyList()))
                    .willReturn(summary);
            given(patchTypeResolver.resolveFromLlmCategory("CHANGE")).willReturn(PatchType.CHANGE);
            given(choseongUtil.extract("LLM제목")).willReturn("ㄹㄹㅁ");

            // When
            listener.handle(event);

            // Then
            ArgumentCaptor<PendingItemCreateRequest> dtoCaptor =
                    ArgumentCaptor.forClass(PendingItemCreateRequest.class);
            then(pendingItemUpsertService).should().upsertPendingItem(dtoCaptor.capture());
            assertThat(dtoCaptor.getValue().title()).isEqualTo("LLM제목");
            assertThat(dtoCaptor.getValue().summary()).isEqualTo("LLM요약내용");
        }

        @Test
        @DisplayName("dto 조립: dto.patchType() 이 PatchTypeResolver 반환값과 일치")
        void handle_dto조립_patchType_resolver반영() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            DocumentSummaryResult summary = new DocumentSummaryResult("제목", "요약", "NEW", true);
            given(vectorStoreRepository.findContentsBySourceId(SOURCE_ID, SourceType.DOCUMENT, 10))
                    .willReturn(List.of("청크1"));
            given(meaningfulnessService.isMeaningful(any(Boolean.class), anyString(), any()))
                    .willReturn(true);
            given(
                            documentSummaryGenerator.generate(
                                    anyString(), anyString(), anyString(), anyList()))
                    .willReturn(summary);
            given(patchTypeResolver.resolveFromLlmCategory("NEW")).willReturn(PatchType.NEW);
            given(choseongUtil.extract("제목")).willReturn("ㅈㅁ");

            // When
            listener.handle(event);

            // Then
            ArgumentCaptor<PendingItemCreateRequest> dtoCaptor =
                    ArgumentCaptor.forClass(PendingItemCreateRequest.class);
            then(pendingItemUpsertService).should().upsertPendingItem(dtoCaptor.capture());
            assertThat(dtoCaptor.getValue().patchType()).isEqualTo(PatchType.NEW);
        }

        @Test
        @DisplayName("dto 조립: sourceCreatedAt 이벤트값 그대로 전달")
        void handle_dto조립_sourceCreatedAt이벤트값전달() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            stubSuccessPath();

            // When
            listener.handle(event);

            // Then
            ArgumentCaptor<PendingItemCreateRequest> dtoCaptor =
                    ArgumentCaptor.forClass(PendingItemCreateRequest.class);
            then(pendingItemUpsertService).should().upsertPendingItem(dtoCaptor.capture());
            assertThat(dtoCaptor.getValue().sourceCreatedAt()).isEqualTo(SOURCE_CREATED_AT);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handle() — 성공 이벤트 발행
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handle() - 성공 이벤트 발행")
    class HandleSuccessEvent {

        @Test
        @DisplayName("성공 흐름: upsertPendingItem 완료 후 DocumentPendingItemCreatedEvent 발행")
        void handle_성공시_DocumentPendingItemCreatedEvent발행() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            stubSuccessPath();

            // When
            listener.handle(event);

            // Then
            DocumentPendingItemCreatedEvent published = captureCreatedEvent();
            assertThat(published.sourceId()).isEqualTo(SOURCE_ID);
            assertThat(published.projectId()).isEqualTo(PROJECT_ID);
        }

        @Test
        @DisplayName("성공 흐름: 이벤트 title이 LLM 생성 title과 일치")
        void handle_성공시_이벤트title_LLM_title일치() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            DocumentSummaryResult summary =
                    new DocumentSummaryResult("LLM생성제목", "요약", "CHANGE", true);
            given(vectorStoreRepository.findContentsBySourceId(SOURCE_ID, SourceType.DOCUMENT, 10))
                    .willReturn(List.of("청크1"));
            given(meaningfulnessService.isMeaningful(any(Boolean.class), anyString(), any()))
                    .willReturn(true);
            given(
                            documentSummaryGenerator.generate(
                                    anyString(), anyString(), anyString(), anyList()))
                    .willReturn(summary);
            given(patchTypeResolver.resolveFromLlmCategory(anyString()))
                    .willReturn(PatchType.CHANGE);
            given(choseongUtil.extract(anyString())).willReturn("ㄹㄹㅁ");

            // When
            listener.handle(event);

            // Then
            DocumentPendingItemCreatedEvent published = captureCreatedEvent();
            assertThat(published.title()).isEqualTo("LLM생성제목");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handle() — 실패 시 예외 전파
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handle() - 실패 시 예외 전파")
    class HandleFailure {

        @Test
        @DisplayName("upsertPendingItem 실패: PendingItemUpsertFailedException → 성공 이벤트 미발행, 예외 전파")
        void handle_upsertFailed_성공이벤트미발행_예외전파() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            stubSuccessPath();
            willThrow(new PendingItemUpsertFailedException(SOURCE_ID))
                    .given(pendingItemUpsertService)
                    .upsertPendingItem(any());

            // When & Then
            assertThatThrownBy(() -> listener.handle(event))
                    .isInstanceOf(PendingItemUpsertFailedException.class);
            then(eventPublisher).should(never()).publishEvent(any());
        }

        @Test
        @DisplayName("upsertPendingItem RuntimeException → 성공 이벤트 미발행, 예외 전파")
        void handle_upsertRuntimeException_예외전파() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            stubSuccessPath();
            willThrow(new RuntimeException("DB 오류"))
                    .given(pendingItemUpsertService)
                    .upsertPendingItem(any());

            // When & Then
            assertThatThrownBy(() -> listener.handle(event)).isInstanceOf(RuntimeException.class);
            then(eventPublisher).should(never()).publishEvent(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handle() — affects_player 업데이트 (best-effort)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handle() - affects_player 업데이트 (best-effort)")
    class HandleAffectsPlayer {

        @Test
        @DisplayName("affects_player 업데이트 성공: updateAffectsPlayerBySourceId 정확한 인자로 호출")
        void handle_affectsPlayer_업데이트호출() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            DocumentSummaryResult summary =
                    new DocumentSummaryResult("제목", "요약", "CHANGE", false); // affectsPlayer=false
            given(vectorStoreRepository.findContentsBySourceId(SOURCE_ID, SourceType.DOCUMENT, 10))
                    .willReturn(List.of("청크1"));
            given(meaningfulnessService.isMeaningful(any(Boolean.class), anyString(), any()))
                    .willReturn(true);
            given(
                            documentSummaryGenerator.generate(
                                    anyString(), anyString(), anyString(), anyList()))
                    .willReturn(summary);
            given(patchTypeResolver.resolveFromLlmCategory(anyString()))
                    .willReturn(PatchType.CHANGE);
            given(choseongUtil.extract(anyString())).willReturn("ㅈㅁ");

            // When
            listener.handle(event);

            // Then
            then(vectorStoreRepository)
                    .should()
                    .updateAffectsPlayerBySourceId(SOURCE_ID, SourceType.DOCUMENT, false);
        }

        @Test
        @DisplayName("affects_player 업데이트 실패 (best-effort): 예외 전파 없이 성공 이벤트 발행")
        void handle_affectsPlayer_업데이트실패_예외삼킴_성공이벤트발행() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            stubSuccessPath();
            willThrow(new RuntimeException("벡터 업데이트 실패"))
                    .given(vectorStoreRepository)
                    .updateAffectsPlayerBySourceId(any(), any(), any(Boolean.class));

            // When (예외 전파 없이 완료되어야 함)
            listener.handle(event);

            // Then — 성공 이벤트는 정상 발행
            then(eventPublisher).should().publishEvent(any(DocumentPendingItemCreatedEvent.class));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handle() — 프로젝트 격리
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handle() - 프로젝트 격리")
    class HandleProjectIsolation {

        @Test
        @DisplayName("프로젝트 격리: 이벤트 projectId가 dto와 성공 이벤트에 그대로 전달")
        void handle_projectId_dto와이벤트에일관되게전달() {
            // Given
            UUID otherProjectId = UUID.randomUUID();
            DocumentEmbeddedEvent event =
                    new DocumentEmbeddedEvent(
                            SOURCE_ID,
                            otherProjectId,
                            1L,
                            "doc.pdf",
                            "그룹",
                            "FIX",
                            true,
                            false,
                            SOURCE_CREATED_AT);
            given(vectorStoreRepository.findContentsBySourceId(SOURCE_ID, SourceType.DOCUMENT, 10))
                    .willReturn(List.of("청크"));
            given(meaningfulnessService.isMeaningful(any(Boolean.class), anyString(), any()))
                    .willReturn(true);
            given(
                            documentSummaryGenerator.generate(
                                    anyString(), anyString(), anyString(), anyList()))
                    .willReturn(new DocumentSummaryResult("제목", "요약", "FIX", true));
            given(patchTypeResolver.resolveFromLlmCategory(anyString())).willReturn(PatchType.FIX);
            given(choseongUtil.extract(anyString())).willReturn("ㅈㅁ");

            // When
            listener.handle(event);

            // Then — dto projectId
            ArgumentCaptor<PendingItemCreateRequest> dtoCaptor =
                    ArgumentCaptor.forClass(PendingItemCreateRequest.class);
            then(pendingItemUpsertService).should().upsertPendingItem(dtoCaptor.capture());
            assertThat(dtoCaptor.getValue().projectId()).isEqualTo(otherProjectId);

            // Then — 성공 이벤트 projectId
            DocumentPendingItemCreatedEvent published = captureCreatedEvent();
            assertThat(published.projectId()).isEqualTo(otherProjectId);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handle() — 처리 순서 보장
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handle() - 처리 순서 보장")
    class HandleOrder {

        @Test
        @DisplayName("순서 보장: documentSummaryGenerator → upsertPendingItem → publishEvent 순서로 호출")
        void handle_InOrder_LLM후_upsert후_publish() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            stubSuccessPath();

            // When
            listener.handle(event);

            // Then
            InOrder order =
                    inOrder(documentSummaryGenerator, pendingItemUpsertService, eventPublisher);
            then(documentSummaryGenerator)
                    .should(order)
                    .generate(anyString(), anyString(), anyString(), anyList());
            then(pendingItemUpsertService).should(order).upsertPendingItem(any());
            then(eventPublisher).should(order).publishEvent(any(Object.class));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handle() — LLM 실패 fallback 통합 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handle() - LLM 실패 fallback 통합")
    class HandleLlmFallback {

        @Test
        @DisplayName("LLM 요약 실패: DocumentSummaryGenerator fallback 반환 → pending_item 정상 적재")
        void handle_LLM실패_fallback결과로_pending_item적재() {
            // Given
            DocumentEmbeddedEvent event = buildEvent(true, false);
            // DocumentSummaryGenerator 자체가 내부적으로 fallback을 반환하는 케이스
            DocumentSummaryResult fallbackResult =
                    new DocumentSummaryResult("design-doc.pdf", "design-doc.pdf", "CHANGE", true);
            given(vectorStoreRepository.findContentsBySourceId(SOURCE_ID, SourceType.DOCUMENT, 10))
                    .willReturn(List.of("청크1"));
            given(meaningfulnessService.isMeaningful(any(Boolean.class), anyString(), any()))
                    .willReturn(true);
            given(
                            documentSummaryGenerator.generate(
                                    anyString(), anyString(), anyString(), anyList()))
                    .willReturn(fallbackResult);
            given(patchTypeResolver.resolveFromLlmCategory("CHANGE")).willReturn(PatchType.CHANGE);
            given(choseongUtil.extract("design-doc.pdf")).willReturn("ㄷ");

            // When (예외 없이 처리 완료)
            listener.handle(event);

            // Then — pending_item upsert 호출됨
            then(pendingItemUpsertService)
                    .should()
                    .upsertPendingItem(any(PendingItemCreateRequest.class));
        }
    }
}

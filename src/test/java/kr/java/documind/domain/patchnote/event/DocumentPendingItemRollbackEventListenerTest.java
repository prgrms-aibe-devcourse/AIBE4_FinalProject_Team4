package kr.java.documind.domain.patchnote.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import java.util.UUID;
import kr.java.documind.domain.archive.document.event.DocumentVectorDeleteEvent;
import kr.java.documind.domain.patchnote.service.PendingItemRollbackService;
import kr.java.documind.global.enums.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentPendingItemRollbackEventListener 단위 테스트")
class DocumentPendingItemRollbackEventListenerTest {

    @InjectMocks private DocumentPendingItemRollbackEventListener listener;

    @Mock private PendingItemRollbackService pendingItemRollbackService;

    private static final Long SOURCE_ID = 10L;
    private static final UUID PROJECT_ID = UUID.randomUUID();

    // ──────────────────────────────────────────────────────────────────────────
    // 픽스처 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    private DocumentVectorDeleteEvent buildEvent() {
        return new DocumentVectorDeleteEvent(PROJECT_ID, SOURCE_ID);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleDocumentDeleted() — 정상 처리
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleDocumentDeleted() - 정상 처리")
    class HandleDocumentDeletedSuccess {

        @Test
        @DisplayName("deleteForRollback: 정확한 인자(projectId, sourceId, DOCUMENT)로 호출")
        void handleDocumentDeleted_deleteForRollback_정확한인자로호출() {
            // Given
            DocumentVectorDeleteEvent event = buildEvent();
            given(
                            pendingItemRollbackService.deleteForRollback(
                                    PROJECT_ID, SOURCE_ID, SourceType.DOCUMENT))
                    .willReturn(true);

            // When
            listener.handleDocumentDeleted(event);

            // Then
            then(pendingItemRollbackService)
                    .should()
                    .deleteForRollback(PROJECT_ID, SOURCE_ID, SourceType.DOCUMENT);
        }

        @Test
        @DisplayName("deleteForRollback=true 반환(PENDING/EXCLUDED hard delete): 정상 완료")
        void handleDocumentDeleted_deleteForRollback_true_정상완료() {
            // Given
            DocumentVectorDeleteEvent event = buildEvent();
            given(
                            pendingItemRollbackService.deleteForRollback(
                                    PROJECT_ID, SOURCE_ID, SourceType.DOCUMENT))
                    .willReturn(true);

            // When & Then (예외 없이 완료)
            assertThatCode(() -> listener.handleDocumentDeleted(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("deleteForRollback=false 반환(COMPLETED soft delete): 정상 완료")
        void handleDocumentDeleted_deleteForRollback_false_정상완료() {
            // Given
            DocumentVectorDeleteEvent event = buildEvent();
            given(
                            pendingItemRollbackService.deleteForRollback(
                                    PROJECT_ID, SOURCE_ID, SourceType.DOCUMENT))
                    .willReturn(false);

            // When & Then (예외 없이 완료)
            assertThatCode(() -> listener.handleDocumentDeleted(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("pending_item 없음(false 반환): deleteForRollback 1회만 호출")
        void handleDocumentDeleted_pendingItem없음_deleteForRollback1회호출() {
            // Given
            DocumentVectorDeleteEvent event = buildEvent();
            given(
                            pendingItemRollbackService.deleteForRollback(
                                    PROJECT_ID, SOURCE_ID, SourceType.DOCUMENT))
                    .willReturn(false);

            // When
            listener.handleDocumentDeleted(event);

            // Then
            then(pendingItemRollbackService)
                    .should()
                    .deleteForRollback(PROJECT_ID, SOURCE_ID, SourceType.DOCUMENT);
            then(pendingItemRollbackService).shouldHaveNoMoreInteractions();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleDocumentDeleted() — 예외 삼킴 (문서 삭제는 이미 커밋 완료)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleDocumentDeleted() - 예외 삼킴")
    class HandleDocumentDeletedExceptionSwallowed {

        @Test
        @DisplayName("deleteForRollback RuntimeException: 예외 삼킴 (문서 삭제 성공 취소 방지)")
        void handleDocumentDeleted_RuntimeException_예외삼킴() {
            // Given
            DocumentVectorDeleteEvent event = buildEvent();
            willThrow(new RuntimeException("DB 오류"))
                    .given(pendingItemRollbackService)
                    .deleteForRollback(PROJECT_ID, SOURCE_ID, SourceType.DOCUMENT);

            // When & Then (예외가 전파되지 않아야 함)
            assertThatCode(() -> listener.handleDocumentDeleted(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("deleteForRollback DataAccessException: 예외 삼킴")
        void handleDocumentDeleted_DataAccessException_예외삼킴() {
            // Given
            DocumentVectorDeleteEvent event = buildEvent();
            willThrow(new org.springframework.dao.DataAccessResourceFailureException("DB 연결 실패"))
                    .given(pendingItemRollbackService)
                    .deleteForRollback(any(), any(), any());

            // When & Then
            assertThatCode(() -> listener.handleDocumentDeleted(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("예외 발생 시: deleteForRollback 이후 추가 작업 없음")
        void handleDocumentDeleted_예외발생시_추가작업없음() {
            // Given
            DocumentVectorDeleteEvent event = buildEvent();
            willThrow(new RuntimeException("오류"))
                    .given(pendingItemRollbackService)
                    .deleteForRollback(PROJECT_ID, SOURCE_ID, SourceType.DOCUMENT);

            // When
            listener.handleDocumentDeleted(event);

            // Then — deleteForRollback만 호출, 이후 추가 메서드 없음
            then(pendingItemRollbackService)
                    .should()
                    .deleteForRollback(PROJECT_ID, SOURCE_ID, SourceType.DOCUMENT);
            then(pendingItemRollbackService).shouldHaveNoMoreInteractions();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleDocumentDeleted() — 프로젝트 격리
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleDocumentDeleted() - 프로젝트 격리")
    class HandleDocumentDeletedProjectIsolation {

        @Test
        @DisplayName("프로젝트 격리: 이벤트 projectId가 deleteForRollback에 그대로 전달")
        void handleDocumentDeleted_다른projectId_격리처리() {
            // Given
            UUID otherProjectId = UUID.randomUUID();
            DocumentVectorDeleteEvent event =
                    new DocumentVectorDeleteEvent(otherProjectId, SOURCE_ID);
            given(
                            pendingItemRollbackService.deleteForRollback(
                                    otherProjectId, SOURCE_ID, SourceType.DOCUMENT))
                    .willReturn(true);

            // When
            listener.handleDocumentDeleted(event);

            // Then
            then(pendingItemRollbackService)
                    .should()
                    .deleteForRollback(otherProjectId, SOURCE_ID, SourceType.DOCUMENT);
        }

        @Test
        @DisplayName("프로젝트 격리: markSourceDeleted 직접 호출 없음 (deleteForRollback에 위임)")
        void handleDocumentDeleted_markSourceDeleted_직접호출없음() {
            // Given
            DocumentVectorDeleteEvent event = buildEvent();
            given(
                            pendingItemRollbackService.deleteForRollback(
                                    PROJECT_ID, SOURCE_ID, SourceType.DOCUMENT))
                    .willReturn(false);

            // When
            listener.handleDocumentDeleted(event);

            // Then — markSourceDeleted는 PendingItemRollbackService 내부에서 처리
            then(pendingItemRollbackService).should(never()).markSourceDeleted(any(), any(), any());
        }
    }
}

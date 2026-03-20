package kr.java.documind.domain.patchnote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.global.enums.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PendingItemRollbackService 단위 테스트")
class PendingItemRollbackServiceTest {

    @Mock private PendingItemRepository pendingItemRepository;

    @InjectMocks private PendingItemRollbackService pendingItemRollbackService;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final Long SOURCE_ID = 42L;
    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    private PendingItem buildItem(PendingItemStatus status) {
        return PendingItem.create(
                PROJECT_ID,
                SOURCE_ID,
                SourceType.ISSUE,
                "제목",
                "요약",
                "ㅈㅇ",
                PatchType.FIX,
                status,
                NOW,
                0,
                null,
                null);
    }

    private PendingItem buildDocItem(PendingItemStatus status, int changeIndex) {
        return PendingItem.create(
                PROJECT_ID,
                SOURCE_ID,
                SourceType.DOCUMENT,
                "문서 제목",
                "문서 요약",
                "ㅁㅅ",
                PatchType.CHANGE,
                status,
                NOW,
                changeIndex,
                null,
                null);
    }

    @Nested
    @DisplayName("deleteForRollback()")
    class DeleteForRollback {

        @Test
        @DisplayName("롤백: PENDING 항목 → bulk hard delete, true 반환")
        void deleteForRollback_PENDING항목_bulkHardDelete후true반환() {
            // Given
            PendingItem item = buildItem(PendingItemStatus.PENDING);
            given(
                            pendingItemRepository.findAllByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(List.of(item));

            // When
            boolean result =
                    pendingItemRollbackService.deleteForRollback(
                            PROJECT_ID, SOURCE_ID, SourceType.ISSUE);

            // Then
            assertThat(result).isTrue();
            then(pendingItemRepository)
                    .should()
                    .deleteByProjectIdAndSourceTypeAndSourceIdIfNotCompleted(
                            PROJECT_ID, SourceType.ISSUE, SOURCE_ID);
        }

        @Test
        @DisplayName("롤백: EXCLUDED 항목 → bulk hard delete, true 반환")
        void deleteForRollback_EXCLUDED항목_bulkHardDelete후true반환() {
            // Given
            PendingItem item = buildItem(PendingItemStatus.EXCLUDED);
            given(
                            pendingItemRepository.findAllByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(List.of(item));

            // When
            boolean result =
                    pendingItemRollbackService.deleteForRollback(
                            PROJECT_ID, SOURCE_ID, SourceType.ISSUE);

            // Then
            assertThat(result).isTrue();
            then(pendingItemRepository)
                    .should()
                    .deleteByProjectIdAndSourceTypeAndSourceIdIfNotCompleted(
                            PROJECT_ID, SourceType.ISSUE, SOURCE_ID);
        }

        @Test
        @DisplayName("롤백: COMPLETED 항목 → markSourceDeleted 처리, false 반환")
        void deleteForRollback_COMPLETED항목_markSourceDeleted처리후false반환() {
            // Given
            PendingItem item = buildItem(PendingItemStatus.PENDING);
            item.complete(); // PENDING → COMPLETED
            given(
                            pendingItemRepository.findAllByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(List.of(item));

            // When
            boolean result =
                    pendingItemRollbackService.deleteForRollback(
                            PROJECT_ID, SOURCE_ID, SourceType.ISSUE);

            // Then
            assertThat(result).isFalse();
            then(pendingItemRepository)
                    .should()
                    .markSourceDeleted(PROJECT_ID, SourceType.ISSUE, SOURCE_ID);
            then(pendingItemRepository)
                    .should(never())
                    .deleteByProjectIdAndSourceTypeAndSourceIdIfNotCompleted(
                            PROJECT_ID, SourceType.ISSUE, SOURCE_ID);
        }

        @Test
        @DisplayName("롤백: 항목 없음 → false 반환, delete 미호출")
        void deleteForRollback_항목없음_false반환() {
            // Given
            given(
                            pendingItemRepository.findAllByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(List.of());

            // When
            boolean result =
                    pendingItemRollbackService.deleteForRollback(
                            PROJECT_ID, SOURCE_ID, SourceType.ISSUE);

            // Then
            assertThat(result).isFalse();
            then(pendingItemRepository)
                    .should(never())
                    .deleteByProjectIdAndSourceTypeAndSourceIdIfNotCompleted(
                            PROJECT_ID, SourceType.ISSUE, SOURCE_ID);
            then(pendingItemRepository)
                    .should(never())
                    .markSourceDeleted(PROJECT_ID, SourceType.ISSUE, SOURCE_ID);
        }

        @Test
        @DisplayName("롤백: DOCUMENT 다중 changeIndex — PENDING + COMPLETED 혼재 → 각각 처리, true 반환")
        void deleteForRollback_DOCUMENT다중changeIndex_혼재시각각처리후true반환() {
            // Given — changeIndex 0은 COMPLETED (기존 패치노트에 포함), changeIndex 1은 PENDING (미사용)
            PendingItem completed = buildDocItem(PendingItemStatus.PENDING, 0);
            completed.complete(); // → COMPLETED
            PendingItem pending = buildDocItem(PendingItemStatus.PENDING, 1);

            given(
                            pendingItemRepository.findAllByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.DOCUMENT, SOURCE_ID))
                    .willReturn(List.of(completed, pending));

            // When
            boolean result =
                    pendingItemRollbackService.deleteForRollback(
                            PROJECT_ID, SOURCE_ID, SourceType.DOCUMENT);

            // Then
            assertThat(result).isTrue(); // PENDING 항목이 있으므로 벡터 삭제 필요
            then(pendingItemRepository)
                    .should()
                    .deleteByProjectIdAndSourceTypeAndSourceIdIfNotCompleted(
                            PROJECT_ID, SourceType.DOCUMENT, SOURCE_ID);
            then(pendingItemRepository)
                    .should()
                    .markSourceDeleted(PROJECT_ID, SourceType.DOCUMENT, SOURCE_ID);
        }

        @Test
        @DisplayName(
                "롤백: DOCUMENT 다중 changeIndex — 모두 COMPLETED → markSourceDeleted 만 호출, false 반환")
        void deleteForRollback_DOCUMENT다중changeIndex_모두COMPLETED시false반환() {
            // Given
            PendingItem c1 = buildDocItem(PendingItemStatus.PENDING, 0);
            c1.complete();
            PendingItem c2 = buildDocItem(PendingItemStatus.PENDING, 1);
            c2.complete();

            given(
                            pendingItemRepository.findAllByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.DOCUMENT, SOURCE_ID))
                    .willReturn(List.of(c1, c2));

            // When
            boolean result =
                    pendingItemRollbackService.deleteForRollback(
                            PROJECT_ID, SOURCE_ID, SourceType.DOCUMENT);

            // Then
            assertThat(result).isFalse();
            then(pendingItemRepository)
                    .should()
                    .markSourceDeleted(PROJECT_ID, SourceType.DOCUMENT, SOURCE_ID);
            then(pendingItemRepository)
                    .should(never())
                    .deleteByProjectIdAndSourceTypeAndSourceIdIfNotCompleted(
                            PROJECT_ID, SourceType.DOCUMENT, SOURCE_ID);
        }
    }

    @Nested
    @DisplayName("markSourceDeleted()")
    class MarkSourceDeleted {

        @Test
        @DisplayName("원본 삭제 처리: repository.markSourceDeleted 호출")
        void markSourceDeleted_호출시_repository메서드호출() {
            // Given — void 메서드, 별도 stubbing 불필요

            // When
            pendingItemRollbackService.markSourceDeleted(PROJECT_ID, SOURCE_ID, SourceType.ISSUE);

            // Then
            then(pendingItemRepository)
                    .should()
                    .markSourceDeleted(PROJECT_ID, SourceType.ISSUE, SOURCE_ID);
        }
    }
}

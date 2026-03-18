package kr.java.documind.domain.patchnote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.global.enums.SourceType;
import org.junit.jupiter.api.BeforeEach;
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
                NOW);
    }

    @Nested
    @DisplayName("deleteForRollback()")
    class DeleteForRollback {

        @Test
        @DisplayName("롤백: PENDING 항목 → hard delete, true 반환")
        void deleteForRollback_PENDING항목_hardDelete후true반환() {
            // Given
            PendingItem item = buildItem(PendingItemStatus.PENDING);
            given(
                            pendingItemRepository.findByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(Optional.of(item));

            // When
            boolean result =
                    pendingItemRollbackService.deleteForRollback(
                            PROJECT_ID, SOURCE_ID, SourceType.ISSUE);

            // Then
            assertThat(result).isTrue();
            then(pendingItemRepository).should().delete(item);
        }

        @Test
        @DisplayName("롤백: EXCLUDED 항목 → hard delete, true 반환")
        void deleteForRollback_EXCLUDED항목_hardDelete후true반환() {
            // Given
            PendingItem item = buildItem(PendingItemStatus.EXCLUDED);
            given(
                            pendingItemRepository.findByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(Optional.of(item));

            // When
            boolean result =
                    pendingItemRollbackService.deleteForRollback(
                            PROJECT_ID, SOURCE_ID, SourceType.ISSUE);

            // Then
            assertThat(result).isTrue();
            then(pendingItemRepository).should().delete(item);
        }

        @Test
        @DisplayName("롤백: COMPLETED 항목 → sourceDeleted 처리, false 반환")
        void deleteForRollback_COMPLETED항목_sourceDeleted처리후false반환() {
            // Given
            PendingItem item = buildItem(PendingItemStatus.PENDING);
            item.complete(); // PENDING → COMPLETED
            given(
                            pendingItemRepository.findByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(Optional.of(item));

            // When
            boolean result =
                    pendingItemRollbackService.deleteForRollback(
                            PROJECT_ID, SOURCE_ID, SourceType.ISSUE);

            // Then
            assertThat(result).isFalse();
            assertThat(item.isSourceDeleted()).isTrue();
            then(pendingItemRepository).should(never()).delete(any());
        }

        @Test
        @DisplayName("롤백: 항목 없음 → false 반환, delete 미호출")
        void deleteForRollback_항목없음_false반환() {
            // Given
            given(
                            pendingItemRepository.findByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(Optional.empty());

            // When
            boolean result =
                    pendingItemRollbackService.deleteForRollback(
                            PROJECT_ID, SOURCE_ID, SourceType.ISSUE);

            // Then
            assertThat(result).isFalse();
            then(pendingItemRepository).should(never()).delete(any());
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

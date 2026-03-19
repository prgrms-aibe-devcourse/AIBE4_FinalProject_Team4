package kr.java.documind.domain.patchnote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PendingItemCommandService 단위 테스트")
class PendingItemCommandServiceTest {

    @Mock private PendingItemRepository pendingItemRepository;

    @InjectMocks private PendingItemCommandService pendingItemCommandService;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OTHER_PROJECT_ID = UUID.randomUUID();
    private static final Long ITEM_ID = 42L;
    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    private PendingItem buildItem(UUID projectId, PendingItemStatus status) {
        PendingItem item = PendingItem.create(
                projectId,
                100L,
                SourceType.ISSUE,
                "테스트 이슈 제목",
                "이슈 요약",
                "ㅌㅅㅡ",
                PatchType.FIX,
                PendingItemStatus.PENDING,
                NOW,
                0, null, null);
        // PENDING이 기본값이므로 EXCLUDED/COMPLETED는 상태 전이
        if (status == PendingItemStatus.EXCLUDED) {
            item.exclude();
        } else if (status == PendingItemStatus.COMPLETED) {
            item.complete();
        }
        return item;
    }

    // ─────────────────────────────────────────────
    // exclude
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("exclude()")
    class Exclude {

        @Test
        @DisplayName("제외: PENDING → EXCLUDED 상태 전이 성공")
        void exclude_PENDING_성공() {
            // Given
            PendingItem item = buildItem(PROJECT_ID, PendingItemStatus.PENDING);
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            // When
            pendingItemCommandService.exclude(PROJECT_ID, ITEM_ID);

            // Then
            assertThat(item.getStatus()).isEqualTo(PendingItemStatus.EXCLUDED);
        }

        @Test
        @DisplayName("제외: EXCLUDED 상태에서 exclude 시도 → BadRequestException")
        void exclude_EXCLUDED상태_BadRequestException() {
            // Given
            PendingItem item = buildItem(PROJECT_ID, PendingItemStatus.EXCLUDED);
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            // When / Then
            assertThatThrownBy(() -> pendingItemCommandService.exclude(PROJECT_ID, ITEM_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("PENDING 상태의 항목만 제외할 수 있습니다");
        }

        @Test
        @DisplayName("제외: COMPLETED 상태에서 exclude 시도 → BadRequestException")
        void exclude_COMPLETED상태_BadRequestException() {
            // Given
            PendingItem item = buildItem(PROJECT_ID, PendingItemStatus.COMPLETED);
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            // When / Then
            assertThatThrownBy(() -> pendingItemCommandService.exclude(PROJECT_ID, ITEM_ID))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("제외: 항목 없음 → NotFoundException")
        void exclude_항목없음_NotFoundException() {
            // Given
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> pendingItemCommandService.exclude(PROJECT_ID, ITEM_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(ITEM_ID));
        }

        @Test
        @DisplayName("제외: 다른 프로젝트 항목 → NotFoundException")
        void exclude_다른프로젝트_NotFoundException() {
            // Given — OTHER_PROJECT_ID 소속 항목을 PROJECT_ID로 접근
            PendingItem item = buildItem(OTHER_PROJECT_ID, PendingItemStatus.PENDING);
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            // When / Then
            assertThatThrownBy(() -> pendingItemCommandService.exclude(PROJECT_ID, ITEM_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(ITEM_ID));
        }
    }

    // ─────────────────────────────────────────────
    // restore
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("restore()")
    class Restore {

        @Test
        @DisplayName("복원: EXCLUDED → PENDING 상태 전이 성공")
        void restore_EXCLUDED_성공() {
            // Given
            PendingItem item = buildItem(PROJECT_ID, PendingItemStatus.EXCLUDED);
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            // When
            pendingItemCommandService.restore(PROJECT_ID, ITEM_ID);

            // Then
            assertThat(item.getStatus()).isEqualTo(PendingItemStatus.PENDING);
        }

        @Test
        @DisplayName("복원: PENDING 상태에서 restore 시도 → BadRequestException")
        void restore_PENDING상태_BadRequestException() {
            // Given
            PendingItem item = buildItem(PROJECT_ID, PendingItemStatus.PENDING);
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            // When / Then
            assertThatThrownBy(() -> pendingItemCommandService.restore(PROJECT_ID, ITEM_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("EXCLUDED 상태의 항목만 복원할 수 있습니다");
        }

        @Test
        @DisplayName("복원: COMPLETED 상태에서 restore 시도 → BadRequestException")
        void restore_COMPLETED상태_BadRequestException() {
            // Given
            PendingItem item = buildItem(PROJECT_ID, PendingItemStatus.COMPLETED);
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            // When / Then
            assertThatThrownBy(() -> pendingItemCommandService.restore(PROJECT_ID, ITEM_ID))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("복원: 항목 없음 → NotFoundException")
        void restore_항목없음_NotFoundException() {
            // Given
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> pendingItemCommandService.restore(PROJECT_ID, ITEM_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(ITEM_ID));
        }

        @Test
        @DisplayName("복원: 다른 프로젝트 항목 → NotFoundException")
        void restore_다른프로젝트_NotFoundException() {
            // Given — OTHER_PROJECT_ID 소속 항목을 PROJECT_ID로 접근
            PendingItem item = buildItem(OTHER_PROJECT_ID, PendingItemStatus.EXCLUDED);
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            // When / Then
            assertThatThrownBy(() -> pendingItemCommandService.restore(PROJECT_ID, ITEM_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(ITEM_ID));
        }
    }
}

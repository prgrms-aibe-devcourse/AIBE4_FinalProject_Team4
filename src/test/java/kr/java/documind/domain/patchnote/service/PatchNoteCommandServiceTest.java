package kr.java.documind.domain.patchnote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteCreateRequest;
import kr.java.documind.domain.patchnote.model.entity.PatchNote;
import kr.java.documind.domain.patchnote.model.repository.PatchNoteRepository;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.global.exception.ConflictException;
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
@DisplayName("PatchNoteCommandService 단위 테스트")
class PatchNoteCommandServiceTest {

    @Mock private PatchNoteRepository patchNoteRepository;
    @Mock private PendingItemRepository pendingItemRepository;

    @InjectMocks private PatchNoteCommandService patchNoteCommandService;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final Long PATCH_NOTE_ID = 1L;

    private PatchNoteCreateRequest buildRequest(
            boolean overwrite, List<Long> itemIds) {
        return new PatchNoteCreateRequest(
                "v1.2.0 업데이트",
                "## 수정\n- 버그 수정",
                1, 2, 0,
                itemIds,
                overwrite);
    }

    // ─────────────────────────────────────────────
    // savePatchNote
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("savePatchNote()")
    class SavePatchNote {

        @Test
        @DisplayName("패치노트 저장: 신규 버전 → 저장 성공, ID 반환")
        void savePatchNote_신규버전_저장성공() {
            // Given
            PatchNoteCreateRequest request = buildRequest(false, List.of());
            given(patchNoteRepository.existsByVersionAndNotDeleted(PROJECT_ID, 1, 2, 0))
                    .willReturn(false);

            PatchNote savedNote = mock(PatchNote.class);
            given(savedNote.getId()).willReturn(PATCH_NOTE_ID);
            given(patchNoteRepository.save(any())).willReturn(savedNote);

            // When
            Long id = patchNoteCommandService.savePatchNote(PROJECT_ID, request);

            // Then
            assertThat(id).isEqualTo(PATCH_NOTE_ID);
            then(patchNoteRepository).should().save(any());
        }

        @Test
        @DisplayName("패치노트 저장: 버전 중복 + overwrite=false → ConflictException")
        void savePatchNote_버전중복_overwriteFalse_ConflictException() {
            // Given
            PatchNoteCreateRequest request = buildRequest(false, List.of());
            given(patchNoteRepository.existsByVersionAndNotDeleted(PROJECT_ID, 1, 2, 0))
                    .willReturn(true);

            // When / Then
            assertThatThrownBy(
                            () -> patchNoteCommandService.savePatchNote(PROJECT_ID, request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("이미 존재하는 버전입니다");
        }

        @Test
        @DisplayName("패치노트 저장: 버전 중복 + overwrite=true → 기존 soft delete 후 신규 저장")
        void savePatchNote_버전중복_overwriteTrue_기존삭제후저장() {
            // Given
            PatchNoteCreateRequest request = buildRequest(true, List.of());
            given(patchNoteRepository.existsByVersionAndNotDeleted(PROJECT_ID, 1, 2, 0))
                    .willReturn(true);

            PatchNote savedNote = mock(PatchNote.class);
            given(savedNote.getId()).willReturn(PATCH_NOTE_ID);
            given(patchNoteRepository.save(any())).willReturn(savedNote);

            // When
            patchNoteCommandService.savePatchNote(PROJECT_ID, request);

            // Then
            then(patchNoteRepository)
                    .should()
                    .softDeleteByVersion(eq(PROJECT_ID), eq(1), eq(2), eq(0), any());
            then(patchNoteRepository).should().save(any());
        }

        @Test
        @DisplayName("패치노트 저장: overwrite=true이지만 버전 없음 → softDeleteByVersion 미호출")
        void savePatchNote_overwriteTrue_버전없음_softDelete미호출() {
            // Given
            PatchNoteCreateRequest request = buildRequest(true, List.of());
            given(patchNoteRepository.existsByVersionAndNotDeleted(PROJECT_ID, 1, 2, 0))
                    .willReturn(false);

            PatchNote savedNote = mock(PatchNote.class);
            given(savedNote.getId()).willReturn(PATCH_NOTE_ID);
            given(patchNoteRepository.save(any())).willReturn(savedNote);

            // When
            patchNoteCommandService.savePatchNote(PROJECT_ID, request);

            // Then
            then(patchNoteRepository).should(never()).softDeleteByVersion(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("패치노트 저장: itemIds 비어있으면 markCompleted 미호출")
        void savePatchNote_itemIds비어있으면_markCompleted미호출() {
            // Given
            PatchNoteCreateRequest request = buildRequest(false, List.of());
            given(patchNoteRepository.existsByVersionAndNotDeleted(PROJECT_ID, 1, 2, 0))
                    .willReturn(false);

            PatchNote savedNote = mock(PatchNote.class);
            given(savedNote.getId()).willReturn(PATCH_NOTE_ID);
            given(patchNoteRepository.save(any())).willReturn(savedNote);

            // When
            patchNoteCommandService.savePatchNote(PROJECT_ID, request);

            // Then
            then(pendingItemRepository).should(never()).markCompleted(any(), any());
        }

        @Test
        @DisplayName("패치노트 저장: itemIds 있으면 markCompleted 호출")
        void savePatchNote_itemIds있으면_markCompleted호출() {
            // Given
            List<Long> itemIds = List.of(10L, 20L, 30L);
            PatchNoteCreateRequest request = buildRequest(false, itemIds);
            given(patchNoteRepository.existsByVersionAndNotDeleted(PROJECT_ID, 1, 2, 0))
                    .willReturn(false);

            PatchNote savedNote = mock(PatchNote.class);
            given(savedNote.getId()).willReturn(PATCH_NOTE_ID);
            given(patchNoteRepository.save(any())).willReturn(savedNote);

            // When
            patchNoteCommandService.savePatchNote(PROJECT_ID, request);

            // Then
            then(pendingItemRepository).should().markCompleted(PROJECT_ID, itemIds);
        }

        @Test
        @DisplayName("패치노트 저장: 음수 버전 → IllegalArgumentException")
        void savePatchNote_음수버전_IllegalArgumentException() {
            // Given
            PatchNoteCreateRequest request =
                    new PatchNoteCreateRequest("제목", "내용", -1, 0, 0, List.of(), false);
            given(patchNoteRepository.existsByVersionAndNotDeleted(PROJECT_ID, -1, 0, 0))
                    .willReturn(false);

            // When / Then
            assertThatThrownBy(
                            () -> patchNoteCommandService.savePatchNote(PROJECT_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("버전은 0 이상이어야 합니다");
        }
    }

    // ─────────────────────────────────────────────
    // deletePatchNote
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("deletePatchNote()")
    class DeletePatchNote {

        @Test
        @DisplayName("패치노트 삭제: soft delete 성공")
        void deletePatchNote_성공() {
            // Given
            given(patchNoteRepository.softDelete(eq(PATCH_NOTE_ID), eq(PROJECT_ID), any()))
                    .willReturn(1);

            // When / Then (예외 없이 정상 완료)
            patchNoteCommandService.deletePatchNote(PROJECT_ID, PATCH_NOTE_ID);

            then(patchNoteRepository).should().softDelete(eq(PATCH_NOTE_ID), eq(PROJECT_ID), any());
        }

        @Test
        @DisplayName("패치노트 삭제: 존재하지 않음 → NotFoundException")
        void deletePatchNote_존재하지않음_NotFoundException() {
            // Given
            given(patchNoteRepository.softDelete(eq(PATCH_NOTE_ID), eq(PROJECT_ID), any()))
                    .willReturn(0);

            // When / Then
            assertThatThrownBy(
                            () -> patchNoteCommandService.deletePatchNote(PROJECT_ID, PATCH_NOTE_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(PATCH_NOTE_ID));
        }

        @Test
        @DisplayName("패치노트 삭제: 이미 삭제된 항목(softDelete 반환 0) → NotFoundException")
        void deletePatchNote_이미삭제됨_NotFoundException() {
            // Given — deletedAt IS NULL 조건으로 0 반환 (이미 soft delete된 경우와 동일)
            given(patchNoteRepository.softDelete(eq(PATCH_NOTE_ID), eq(PROJECT_ID), any()))
                    .willReturn(0);

            // When / Then
            assertThatThrownBy(
                            () -> patchNoteCommandService.deletePatchNote(PROJECT_ID, PATCH_NOTE_ID))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}

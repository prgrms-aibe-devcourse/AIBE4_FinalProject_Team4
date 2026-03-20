package kr.java.documind.domain.patchnote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteDetail;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteSummary;
import kr.java.documind.domain.patchnote.model.entity.PatchNote;
import kr.java.documind.domain.patchnote.model.repository.PatchNoteRepository;
import kr.java.documind.global.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatchNoteQueryService 단위 테스트")
class PatchNoteQueryServiceTest {

    @Mock private PatchNoteRepository patchNoteRepository;

    @InjectMocks private PatchNoteQueryService patchNoteQueryService;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OTHER_PROJECT_ID = UUID.randomUUID();
    private static final Long PATCH_NOTE_ID = 1L;

    private PatchNote buildDraft(UUID projectId) {
        return PatchNote.createDraft(projectId, "v1.2.0 업데이트", "## 변경 내용", 1, 2, 0);
    }

    // ─────────────────────────────────────────────
    // listPatchNotes
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("listPatchNotes()")
    class ListPatchNotes {

        @Test
        @DisplayName("패치노트 목록: 빈 결과 → 빈 목록 반환")
        void listPatchNotes_빈목록반환() {
            // Given
            given(patchNoteRepository.findAllByProjectIdAndNotDeleted(PROJECT_ID))
                    .willReturn(List.of());

            // When
            List<PatchNoteSummary> result = patchNoteQueryService.listPatchNotes(PROJECT_ID);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("패치노트 목록: 항목 있을 때 → PatchNoteSummary 목록 반환")
        void listPatchNotes_항목있을때_요약목록반환() {
            // Given
            PatchNote note1 = buildDraft(PROJECT_ID);
            PatchNote note2 = PatchNote.createDraft(PROJECT_ID, "v1.3.0", "내용", 1, 3, 0);
            given(patchNoteRepository.findAllByProjectIdAndNotDeleted(PROJECT_ID))
                    .willReturn(List.of(note1, note2));

            // When
            List<PatchNoteSummary> result = patchNoteQueryService.listPatchNotes(PROJECT_ID);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).versionLabel()).isEqualTo("v1.2.0");
            assertThat(result.get(1).versionLabel()).isEqualTo("v1.3.0");
        }
    }

    // ─────────────────────────────────────────────
    // getDetail
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("getDetail()")
    class GetDetail {

        @Test
        @DisplayName("패치노트 상세: 정상 조회 → PatchNoteDetail 반환")
        void getDetail_성공() {
            // Given
            PatchNote patchNote = buildDraft(PROJECT_ID);
            given(patchNoteRepository.findByIdAndDeletedAtIsNull(PATCH_NOTE_ID))
                    .willReturn(Optional.of(patchNote));

            // When
            PatchNoteDetail detail = patchNoteQueryService.getDetail(PROJECT_ID, PATCH_NOTE_ID);

            // Then
            assertThat(detail).isNotNull();
            assertThat(detail.versionLabel()).isEqualTo("v1.2.0");
            assertThat(detail.title()).isEqualTo("v1.2.0 업데이트");
        }

        @Test
        @DisplayName("패치노트 상세: 존재하지 않음(또는 이미 삭제) → NotFoundException")
        void getDetail_존재하지않음_NotFoundException() {
            // Given
            given(patchNoteRepository.findByIdAndDeletedAtIsNull(PATCH_NOTE_ID))
                    .willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> patchNoteQueryService.getDetail(PROJECT_ID, PATCH_NOTE_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(PATCH_NOTE_ID));
        }

        @Test
        @DisplayName("패치노트 상세: 다른 프로젝트 소속 → NotFoundException")
        void getDetail_다른프로젝트_NotFoundException() {
            // Given — OTHER_PROJECT_ID 소속 패치노트를 PROJECT_ID로 조회
            PatchNote patchNote = buildDraft(OTHER_PROJECT_ID);
            given(patchNoteRepository.findByIdAndDeletedAtIsNull(PATCH_NOTE_ID))
                    .willReturn(Optional.of(patchNote));

            // When / Then
            assertThatThrownBy(() -> patchNoteQueryService.getDetail(PROJECT_ID, PATCH_NOTE_ID))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}

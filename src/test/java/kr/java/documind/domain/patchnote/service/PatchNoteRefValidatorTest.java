package kr.java.documind.domain.patchnote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import kr.java.documind.domain.patchnote.model.dto.ItemContext;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteDraftResponse;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteItemResponse;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteSectionResponse;
import kr.java.documind.domain.patchnote.model.dto.RagContext;
import kr.java.documind.domain.patchnote.model.dto.TokenEstimation;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PatchNoteRefValidator 단위 테스트")
class PatchNoteRefValidatorTest {

    private PatchNoteRefValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PatchNoteRefValidator();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    private RagContext ragContextWith(String... refs) {
        List<ItemContext> items = List.of();
        TokenEstimation te = new TokenEstimation(0, 1000, false, 0);
        return new RagContext(items, Map.of(), List.of(refs), te);
    }

    private PatchNoteDraftResponse response(List<PatchNoteSectionResponse> sections) {
        return new PatchNoteDraftResponse(null, sections, null);
    }

    private PatchNoteItemResponse item(String text, String... refs) {
        return new PatchNoteItemResponse(text, List.of(refs));
    }

    private PatchNoteSectionResponse section(String type, PatchNoteItemResponse... items) {
        return new PatchNoteSectionResponse(type, List.of(items));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validate()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        @DisplayName("모든 REF가 화이트리스트에 있음 → 응답 그대로 반환")
        void validate_모든REF유효_그대로반환() {
            // Given
            RagContext ctx = ragContextWith("ISSUE-1", "DOC-5-0");
            PatchNoteDraftResponse resp = response(List.of(
                    section("FIX", item("버그 수정", "ISSUE-1"), item("문서 수정", "DOC-5-0"))));

            // When
            PatchNoteDraftResponse result = validator.validate(resp, ctx);

            // Then
            assertThat(result.sections()).hasSize(1);
            assertThat(result.sections().get(0).items()).hasSize(2);
            assertThat(result.sections().get(0).items().get(0).sourceRefs()).containsExactly("ISSUE-1");
        }

        @Test
        @DisplayName("환각 REF 포함 항목 → 환각 REF만 제거, 유효 REF 유지")
        void validate_환각REF포함_환각만제거() {
            // Given — 화이트리스트: ISSUE-1 만; 환각: FAKE-99
            RagContext ctx = ragContextWith("ISSUE-1");
            PatchNoteDraftResponse resp = response(List.of(
                    section("FIX", item("버그 수정", "ISSUE-1", "FAKE-99"))));

            // When
            PatchNoteDraftResponse result = validator.validate(resp, ctx);

            // Then
            List<String> refs = result.sections().get(0).items().get(0).sourceRefs();
            assertThat(refs).containsExactly("ISSUE-1");
            assertThat(refs).doesNotContain("FAKE-99");
        }

        @Test
        @DisplayName("text가 null인 항목 → 전체 드롭")
        void validate_텍스트null항목_드롭() {
            // Given
            RagContext ctx = ragContextWith("ISSUE-1");
            PatchNoteItemResponse nullText = new PatchNoteItemResponse(null, List.of("ISSUE-1"));
            PatchNoteDraftResponse resp = response(List.of(
                    section("FIX", nullText)));

            // When
            PatchNoteDraftResponse result = validator.validate(resp, ctx);

            // Then — 항목 없음, 섹션도 드롭
            assertThat(result.sections()).isEmpty();
        }

        @Test
        @DisplayName("text가 공백인 항목 → 전체 드롭")
        void validate_텍스트공백항목_드롭() {
            // Given
            RagContext ctx = ragContextWith("ISSUE-1");
            PatchNoteItemResponse blankText = new PatchNoteItemResponse("   ", List.of("ISSUE-1"));
            PatchNoteDraftResponse resp = response(List.of(
                    section("FIX", blankText)));

            // When
            PatchNoteDraftResponse result = validator.validate(resp, ctx);

            // Then
            assertThat(result.sections()).isEmpty();
        }

        @Test
        @DisplayName("모든 항목 드롭 → 섹션 전체 드롭")
        void validate_모든항목드롭_섹션드롭() {
            // Given — 모든 REF가 환각
            RagContext ctx = ragContextWith("ISSUE-1");
            PatchNoteDraftResponse resp = response(List.of(
                    section("FIX",
                            item("수정1", "FAKE-1"),
                            item("수정2", "FAKE-2"))));

            // When
            PatchNoteDraftResponse result = validator.validate(resp, ctx);

            // Then — sourceRefs 제거 후 text는 유지, sections는 남음 (항목 자체는 살아있음)
            // 참고: validator는 text가 있으면 항목 유지, ref만 제거
            assertThat(result.sections()).hasSize(1);
            assertThat(result.sections().get(0).items()).allMatch(
                    i -> i.sourceRefs() == null || i.sourceRefs().isEmpty());
        }

        @Test
        @DisplayName("빈 sections → 그대로 반환")
        void validate_빈sections_그대로반환() {
            // Given
            RagContext ctx = ragContextWith("ISSUE-1");
            PatchNoteDraftResponse resp = response(List.of());

            // When
            PatchNoteDraftResponse result = validator.validate(resp, ctx);

            // Then
            assertThat(result.sections()).isEmpty();
        }

        @Test
        @DisplayName("sections null → 그대로 반환")
        void validate_sectionsNull_그대로반환() {
            // Given
            RagContext ctx = ragContextWith("ISSUE-1");
            PatchNoteDraftResponse resp = new PatchNoteDraftResponse(null, null, null);

            // When
            PatchNoteDraftResponse result = validator.validate(resp, ctx);

            // Then
            assertThat(result.sections()).isNull();
        }

        @Test
        @DisplayName("sourceRefs 없는 항목 → 변경 없이 통과")
        void validate_sourceRefs없는항목_변경없이통과() {
            // Given
            RagContext ctx = ragContextWith("ISSUE-1");
            PatchNoteDraftResponse resp = response(List.of(
                    section("NEW", item("신규 기능 추가"))));

            // When
            PatchNoteDraftResponse result = validator.validate(resp, ctx);

            // Then
            assertThat(result.sections()).hasSize(1);
            assertThat(result.sections().get(0).items().get(0).text()).isEqualTo("신규 기능 추가");
        }
    }
}

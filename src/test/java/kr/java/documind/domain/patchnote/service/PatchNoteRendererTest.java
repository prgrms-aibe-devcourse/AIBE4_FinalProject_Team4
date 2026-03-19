package kr.java.documind.domain.patchnote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.java.documind.domain.patchnote.model.dto.DraftResult;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteDraftResponse;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteItemResponse;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteSectionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PatchNoteRenderer 단위 테스트")
class PatchNoteRendererTest {

    private PatchNoteRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new PatchNoteRenderer();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    private PatchNoteItemResponse item(String text, String... refs) {
        return new PatchNoteItemResponse(text, List.of(refs));
    }

    private PatchNoteSectionResponse section(String type, PatchNoteItemResponse... items) {
        return new PatchNoteSectionResponse(type, List.of(items));
    }

    private PatchNoteDraftResponse response(PatchNoteSectionResponse... sections) {
        return new PatchNoteDraftResponse(null, List.of(sections), null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // render()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("render()")
    class Render {

        @Test
        @DisplayName("빈 sections → 빈 DraftResult 반환")
        void render_빈sections_빈DraftResult반환() {
            // Given
            PatchNoteDraftResponse resp = new PatchNoteDraftResponse(null, List.of(), null);

            // When
            DraftResult result = renderer.render(resp);

            // Then
            assertThat(result.cleanedContent()).isEmpty();
            assertThat(result.sourceRefs()).isEmpty();
        }

        @Test
        @DisplayName("sections null → 빈 DraftResult 반환")
        void render_sectionsNull_빈DraftResult반환() {
            // Given
            PatchNoteDraftResponse resp = new PatchNoteDraftResponse(null, null, null);

            // When
            DraftResult result = renderer.render(resp);

            // Then
            assertThat(result.cleanedContent()).isEmpty();
            assertThat(result.sourceRefs()).isEmpty();
        }

        @Test
        @DisplayName("단일 섹션 렌더링 → H2 헤더 + 불릿포인트 포함")
        void render_단일섹션_H2헤더불릿포인트포함() {
            // Given
            PatchNoteDraftResponse resp = response(
                    section("FIX", item("전투 밸런스 버그 수정", "ISSUE-42")));

            // When
            DraftResult result = renderer.render(resp);

            // Then
            assertThat(result.cleanedContent()).contains("## 수정");
            assertThat(result.cleanedContent()).contains("- 전투 밸런스 버그 수정");
            assertThat(result.cleanedContent()).contains("{{source:ISSUE-42}}");
        }

        @Test
        @DisplayName("섹션 순서 강제: 입력이 FIX→NEW 순서여도 NEW→FIX 순서로 출력")
        void render_섹션순서강제_NEW먼저출력() {
            // Given — 의도적으로 FIX를 NEW보다 먼저 입력
            PatchNoteDraftResponse resp = response(
                    section("FIX",  item("버그 수정")),
                    section("NEW",  item("신규 기능")),
                    section("CHANGE", item("밸런스 조정")));

            // When
            DraftResult result = renderer.render(resp);

            // Then — NEW가 FIX보다 앞에 나와야 함
            String content = result.cleanedContent();
            int newIdx    = content.indexOf("## 신규");
            int changeIdx = content.indexOf("## 변경");
            int fixIdx    = content.indexOf("## 수정");
            assertThat(newIdx).isLessThan(changeIdx);
            assertThat(changeIdx).isLessThan(fixIdx);
        }

        @Test
        @DisplayName("섹션 전체 순서: NEW → CHANGE → FIX → MAINTENANCE")
        void render_전체순서_정규순서대로출력() {
            // Given — 역순 입력
            PatchNoteDraftResponse resp = response(
                    section("MAINTENANCE", item("유지보수")),
                    section("FIX",         item("수정")),
                    section("CHANGE",      item("변경")),
                    section("NEW",         item("신규")));

            // When
            DraftResult result = renderer.render(resp);

            // Then
            String content = result.cleanedContent();
            int newIdx  = content.indexOf("## 신규");
            int chgIdx  = content.indexOf("## 변경");
            int fixIdx  = content.indexOf("## 수정");
            int mntIdx  = content.indexOf("## 유지보수");
            assertThat(newIdx).isLessThan(chgIdx);
            assertThat(chgIdx).isLessThan(fixIdx);
            assertThat(fixIdx).isLessThan(mntIdx);
        }

        @Test
        @DisplayName("비표준 섹션 타입 → 정규 섹션 뒤에 추가")
        void render_비표준섹션_정규섹션뒤에추가() {
            // Given
            PatchNoteDraftResponse resp = response(
                    section("UNKNOWN_TYPE", item("알 수 없는 항목")),
                    section("FIX",          item("버그 수정")));

            // When
            DraftResult result = renderer.render(resp);

            // Then — FIX가 UNKNOWN_TYPE보다 앞에 나와야 함
            String content = result.cleanedContent();
            int fixIdx     = content.indexOf("## 수정");
            int unknownIdx = content.indexOf("## UNKNOWN_TYPE");
            assertThat(fixIdx).isLessThan(unknownIdx);
        }

        @Test
        @DisplayName("sourceRefs 수집 — 등장 순서 보장, 중복 제거")
        void render_sourceRefs수집_등장순서보장_중복제거() {
            // Given — ISSUE-1 → DOC-5 → ISSUE-1 (중복) 순서로 등장
            PatchNoteDraftResponse resp = response(
                    section("FIX",
                            item("수정 A", "ISSUE-1"),
                            item("수정 B", "DOC-5", "ISSUE-1")));

            // When
            DraftResult result = renderer.render(resp);

            // Then — ISSUE-1이 먼저, DOC-5가 두 번째, 중복 없음
            assertThat(result.sourceRefs()).containsExactly("ISSUE-1", "DOC-5");
        }

        @Test
        @DisplayName("빈 text 항목 → 렌더링 스킵")
        void render_빈텍스트항목_스킵() {
            // Given
            PatchNoteDraftResponse resp = response(
                    section("NEW",
                            item(""),
                            item("   "),
                            item("유효한 항목")));

            // When
            DraftResult result = renderer.render(resp);

            // Then — 유효한 항목 하나만 렌더링
            long bulletCount = result.cleanedContent().lines()
                    .filter(line -> line.startsWith("- "))
                    .count();
            assertThat(bulletCount).isEqualTo(1);
        }

        @Test
        @DisplayName("sourceRefs 없는 항목 → 태그 없이 렌더링")
        void render_sourceRefs없는항목_태그없이렌더링() {
            // Given
            PatchNoteDraftResponse resp = response(
                    section("NEW", item("태그 없는 항목")));

            // When
            DraftResult result = renderer.render(resp);

            // Then
            assertThat(result.cleanedContent()).contains("- 태그 없는 항목");
            assertThat(result.cleanedContent()).doesNotContain("{{source:");
            assertThat(result.sourceRefs()).isEmpty();
        }
    }
}

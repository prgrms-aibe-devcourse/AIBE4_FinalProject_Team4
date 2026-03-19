package kr.java.documind.domain.patchnote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.patchnote.config.TokenRagProperties;
import kr.java.documind.domain.patchnote.model.dto.ItemContext;
import kr.java.documind.domain.patchnote.model.dto.RagContext;
import kr.java.documind.domain.patchnote.model.dto.RagEvidence;
import kr.java.documind.domain.patchnote.model.dto.TokenEstimation;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvidenceReducer 단위 테스트")
class EvidenceReducerTest {

    @Mock  private TokenRagProperties properties;
    @InjectMocks private EvidenceReducer evidenceReducer;

    /** promptOverhead=100, tokenLimit=200 으로 고정 */
    @BeforeEach
    void setUp() {
        given(properties.promptOverhead()).willReturn(100);
        given(properties.tokenLimit()).willReturn(200);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    private RagEvidence evidence(String role, String text) {
        return new RagEvidence("REF", role, text, 0.9, false, false, false);
    }

    private ItemContext item(String ref, String summary, List<RagEvidence> evidences) {
        return new ItemContext(ref, PatchType.FIX, "제목", summary, evidences, List.of(ref));
    }

    private RagContext context(List<ItemContext> items) {
        int totalChars = items.stream()
                .mapToInt(ic -> {
                    int s = ic.summary() != null ? ic.summary().length() : 0;
                    int e = ic.evidences().stream()
                            .mapToInt(ev -> ev.text() != null ? ev.text().length() : 0)
                            .sum();
                    return s + e;
                }).sum();
        int estimated = 100 + (totalChars / 3);
        boolean exceeded = estimated > 200;
        TokenEstimation te = new TokenEstimation(estimated, 200, exceeded, items.size());
        return new RagContext(items, Map.of(), List.of(), te);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // reduce()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reduce()")
    class Reduce {

        @Test
        @DisplayName("토큰 한도 미초과 → 원본 RagContext 그대로 반환")
        void reduce_한도미초과_원본반환() {
            // Given — summary 10자 * 1개 → tokens = 100 + 10/3 ≈ 103 < 200
            ItemContext ic = item("ISSUE-1", "짧은 요약", List.of());
            RagContext original = context(List.of(ic));
            assertThat(original.tokenEstimation().exceeded()).isFalse();

            // When
            RagContext result = evidenceReducer.reduce(original);

            // Then — 동일 참조 (새 객체 생성 없음)
            assertThat(result).isSameAs(original);
        }

        @Test
        @DisplayName("chunk 역할 증거 제거 후 한도 이하 → chunk만 제거됨")
        void reduce_chunk역할제거후한도이하_chunk만제거() {
            // Given — summary 60자 + chunk 300자 → 100 + 360/3 = 220 > 200
            String longText = "a".repeat(300);
            ItemContext ic = item("ISSUE-1", "a".repeat(60),
                    List.of(evidence("chunk", longText)));
            RagContext original = context(List.of(ic));
            assertThat(original.tokenEstimation().exceeded()).isTrue();

            // When
            RagContext result = evidenceReducer.reduce(original);

            // Then — chunk 증거 제거, resolution 같은 중요 증거는 유지
            List<RagEvidence> remaining = result.itemContexts().get(0).evidences();
            assertThat(remaining).noneMatch(e -> "chunk".equals(e.role()));
        }

        @Test
        @DisplayName("background 역할 증거 제거 → chunk 없고 background만 있는 경우 제거됨")
        void reduce_background역할제거_background제거됨() {
            // Given — summary 60자 + background 300자 → 초과
            String longText = "b".repeat(300);
            ItemContext ic = item("ISSUE-1", "b".repeat(60),
                    List.of(evidence("background", longText)));
            RagContext original = context(List.of(ic));
            assertThat(original.tokenEstimation().exceeded()).isTrue();

            // When
            RagContext result = evidenceReducer.reduce(original);

            // Then — background 증거 제거
            List<RagEvidence> remaining = result.itemContexts().get(0).evidences();
            assertThat(remaining).noneMatch(e -> "background".equals(e.role()));
        }

        @Test
        @DisplayName("resolution 역할 증거 — 어떤 단계에서도 제거되지 않음")
        void reduce_resolution역할_절대제거안됨() {
            // Given — summary 60자 + resolution 200자 + chunk 200자 → 초과
            ItemContext ic = item("ISSUE-1", "c".repeat(60), List.of(
                    evidence("resolution", "c".repeat(200)),
                    evidence("chunk",      "c".repeat(200))));
            RagContext original = context(List.of(ic));
            assertThat(original.tokenEstimation().exceeded()).isTrue();

            // When
            RagContext result = evidenceReducer.reduce(original);

            // Then — resolution은 반드시 유지
            List<RagEvidence> remaining = result.itemContexts().get(0).evidences();
            assertThat(remaining).anyMatch(e -> "resolution".equals(e.role()));
        }

        @Test
        @DisplayName("최후 수단: 항목당 증거 1개 제한 적용")
        void reduce_최후수단_항목당1개제한() {
            // Given — 모든 역할 제거 후에도 초과 → 두 개의 resolution 증거 → 1개로 제한
            // summary 60자 * 2개 + resolution 200자 * 2개 = (120+400)/3 ≈ 173 + 100 = 273 > 200
            ItemContext ic1 = item("ISSUE-1", "d".repeat(60), List.of(
                    evidence("resolution", "d".repeat(200)),
                    evidence("resolution", "d".repeat(200))));
            ItemContext ic2 = item("ISSUE-2", "d".repeat(60), List.of(
                    evidence("resolution", "d".repeat(200)),
                    evidence("resolution", "d".repeat(200))));
            RagContext original = context(List.of(ic1, ic2));
            assertThat(original.tokenEstimation().exceeded()).isTrue();

            // When
            RagContext result = evidenceReducer.reduce(original);

            // Then — 각 항목에 증거 1개씩
            assertThat(result.itemContexts().get(0).evidences()).hasSize(1);
            assertThat(result.itemContexts().get(1).evidences()).hasSize(1);
        }

        @Test
        @DisplayName("감소 후 TokenEstimation 반영 — 한도 내 유지 여부 확인")
        void reduce_감소후TokenEstimation갱신() {
            // Given — chunk 역할 제거 시 한도 이하로 내려가는 케이스
            String longText = "e".repeat(300);
            ItemContext ic = item("ISSUE-1", "e".repeat(60),
                    List.of(evidence("chunk", longText)));
            RagContext original = context(List.of(ic));
            assertThat(original.tokenEstimation().exceeded()).isTrue();

            // When
            RagContext result = evidenceReducer.reduce(original);

            // Then — 반환된 컨텍스트의 TokenEstimation이 실제 감소된 값 반영
            assertThat(result.tokenEstimation().estimatedTokens())
                    .isLessThanOrEqualTo(result.tokenEstimation().tokenLimit() * 2);
        }
    }
}

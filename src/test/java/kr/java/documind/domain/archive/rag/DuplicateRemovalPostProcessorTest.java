package kr.java.documind.domain.archive.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

@DisplayName("DuplicateRemovalPostProcessor")
class DuplicateRemovalPostProcessorTest {

    private final DuplicateRemovalPostProcessor processor = new DuplicateRemovalPostProcessor();
    private final Query query = new Query("test query");

    private Document doc(String id, String text, String sourceId) {
        return Document.builder().id(id).text(text).metadata("source_id", sourceId).build();
    }

    private Document docWithoutSource(String id, String text) {
        return Document.builder().id(id).text(text).build();
    }

    @Nested
    @DisplayName("ID 기반 중복 제거")
    class IdDeduplication {

        @Test
        @DisplayName("중복 없는 문서 리스트는 그대로 반환한다")
        void noDuplicates_ReturnsAll() {
            List<Document> docs = List.of(doc("1", "텍스트A", "src1"), doc("2", "텍스트B", "src2"));

            List<Document> result = processor.process(query, docs);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("동일 ID 중복 시 첫 번째만 유지한다")
        void duplicateIds_KeepsFirst() {
            Document first = doc("1", "텍스트A", "src1");
            Document duplicate = doc("1", "텍스트A 복사", "src1");

            List<Document> result = processor.process(query, List.of(first, duplicate));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getText()).isEqualTo("텍스트A");
        }
    }

    @Nested
    @DisplayName("오버랩 청크 제거")
    class OverlappingChunkRemoval {

        @Test
        @DisplayName("같은 source에서 A가 B를 포함하면 B를 제거한다")
        void sameSource_AContainsB_RemovesB() {
            Document longer = doc("1", "이것은 긴 텍스트입니다 전체 내용", "src1");
            Document shorter = doc("2", "긴 텍스트입니다", "src1");

            List<Document> result = processor.process(query, List.of(longer, shorter));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("1");
        }

        @Test
        @DisplayName("같은 source에서 B가 A를 포함하면 A를 제거한다")
        void sameSource_BContainsA_RemovesA() {
            Document shorter = doc("1", "짧은 텍스트", "src1");
            Document longer = doc("2", "이것은 짧은 텍스트를 포함하는 긴 문장입니다", "src1");

            List<Document> result = processor.process(query, List.of(shorter, longer));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("2");
        }

        @Test
        @DisplayName("다른 source끼리는 텍스트가 겹쳐도 제거하지 않는다")
        void differentSource_NoRemoval() {
            Document doc1 = doc("1", "공통 텍스트 내용", "src1");
            Document doc2 = doc("2", "공통 텍스트 내용", "src2");

            List<Document> result = processor.process(query, List.of(doc1, doc2));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("text가 null인 문서는 오버랩 비교를 건너뛴다")
        void nullText_SkipsOverlapCheck() {
            Document nullText = doc("1", null, "src1");
            Document normal = doc("2", "정상 텍스트", "src1");

            List<Document> result = processor.process(query, List.of(nullText, normal));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("source_id가 null이면 오버랩 비교를 건너뛴다")
        void nullSourceId_SkipsOverlapCheck() {
            Document noSource = docWithoutSource("1", "공통 텍스트");
            Document normal = doc("2", "공통 텍스트", "src1");

            List<Document> result = processor.process(query, List.of(noSource, normal));

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("빈 리스트는 빈 리스트를 반환한다")
        void emptyList_ReturnsEmpty() {
            List<Document> result = processor.process(query, List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("ID 중복과 오버랩이 동시에 발생하면 모두 제거한다")
        void idDuplicateAndOverlap_RemovesBoth() {
            Document doc1 = doc("1", "전체 긴 텍스트 내용입니다", "src1");
            Document doc2 = doc("1", "전체 긴 텍스트 내용입니다 복사", "src1");
            Document doc3 = doc("3", "긴 텍스트", "src1");

            List<Document> result = processor.process(query, List.of(doc1, doc2, doc3));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("1");
        }
    }
}

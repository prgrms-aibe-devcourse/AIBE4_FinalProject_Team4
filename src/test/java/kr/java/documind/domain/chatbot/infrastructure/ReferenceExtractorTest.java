package kr.java.documind.domain.chatbot.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.chatbot.model.dto.response.ReferenceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;

@DisplayName("ReferenceExtractor")
@ExtendWith(MockitoExtension.class)
class ReferenceExtractorTest {

    @Mock
    private DocumentMetadataManager documentMetadataManager;

    @InjectMocks
    private ReferenceExtractor referenceExtractor;

    private Document createDocument(Long sourceId, Integer pageNumber, String text) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        if (sourceId != null) {
            metadata.put("source_id", String.valueOf(sourceId));
        }
        if (pageNumber != null) {
            metadata.put("page_number", String.valueOf(pageNumber));
        }
        return new Document(text != null ? text : "", metadata);
    }

    private ChatClientResponse createResponseWithDocs(List<Document> docs) {
        ChatClientResponse response = mock(ChatClientResponse.class);
        Map<String, Object> context =
                Map.of(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, docs);
        given(response.context()).willReturn(context);
        return response;
    }

    private DocumentMetadata mockDocumentMetadata(
            Long id, String name, String extension, String version) {
        DocumentMetadata metadata = mock(DocumentMetadata.class);
        given(metadata.getId()).willReturn(id);
        given(metadata.getDocumentName()).willReturn(name);
        given(metadata.getExtension()).willReturn(extension);
        given(metadata.getVersionString()).willReturn(version);
        return metadata;
    }

    @Nested
    @DisplayName("extract")
    class Extract {

        @Test
        @DisplayName("context가 null이면 빈 리스트를 반환한다")
        void extract_NullContext_ReturnsEmptyList() {
            ChatClientResponse response = mock(ChatClientResponse.class);
            given(response.context()).willReturn(null);

            List<ReferenceResponse> result = referenceExtractor.extract(response);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("DOCUMENT_CONTEXT가 null이면 빈 리스트를 반환한다")
        void extract_NullDocumentContext_ReturnsEmptyList() {
            ChatClientResponse response = mock(ChatClientResponse.class);
            Map<String, Object> context = new java.util.HashMap<>();
            context.put(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, null);
            given(response.context()).willReturn(context);

            List<ReferenceResponse> result = referenceExtractor.extract(response);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("문서 목록이 비어 있으면 빈 리스트를 반환한다")
        void extract_EmptyDocuments_ReturnsEmptyList() {
            ChatClientResponse response = createResponseWithDocs(List.of());

            List<ReferenceResponse> result = referenceExtractor.extract(response);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("정상 문서를 ReferenceResponse로 변환한다")
        void extract_ValidDocuments_ReturnsReferences() {
            Document doc = createDocument(1L, 3, "청크 텍스트");
            ChatClientResponse response = createResponseWithDocs(List.of(doc));

            DocumentMetadata metadata = mockDocumentMetadata(1L, "문서A", "pdf", "v1.0.0");
            given(documentMetadataManager.findMapByIds(Set.of(1L)))
                    .willReturn(Map.of(1L, metadata));

            List<ReferenceResponse> result = referenceExtractor.extract(response);

            assertThat(result).hasSize(1);
            ReferenceResponse ref = result.get(0);
            assertThat(ref.documentId()).isEqualTo(1L);
            assertThat(ref.documentName()).isEqualTo("문서A");
            assertThat(ref.extension()).isEqualTo("pdf");
            assertThat(ref.version()).isEqualTo("v1.0.0");
            assertThat(ref.pageNumber()).isEqualTo(3);
            assertThat(ref.chunkText()).isEqualTo("청크 텍스트");
        }

        @Test
        @DisplayName("여러 문서를 순서대로 변환한다")
        void extract_MultipleDocuments_ReturnsInOrder() {
            Document doc1 = createDocument(1L, 1, "텍스트1");
            Document doc2 = createDocument(2L, 5, "텍스트2");
            ChatClientResponse response = createResponseWithDocs(List.of(doc1, doc2));

            DocumentMetadata meta1 = mockDocumentMetadata(1L, "문서A", "pdf", "v1.0.0");
            DocumentMetadata meta2 = mockDocumentMetadata(2L, "문서B", "docx", "v2.1.0");
            given(documentMetadataManager.findMapByIds(Set.of(1L, 2L)))
                    .willReturn(Map.of(1L, meta1, 2L, meta2));

            List<ReferenceResponse> result = referenceExtractor.extract(response);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).documentName()).isEqualTo("문서A");
            assertThat(result.get(1).documentName()).isEqualTo("문서B");
        }

        @Test
        @DisplayName("source_id가 없는 문서는 건너뛴다")
        void extract_NoSourceId_Skipped() {
            Document doc = createDocument(null, 1, "텍스트");
            ChatClientResponse response = createResponseWithDocs(List.of(doc));

            List<ReferenceResponse> result = referenceExtractor.extract(response);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("DB에 메타데이터가 없는 문서는 건너뛴다")
        void extract_MetadataNotFound_Skipped() {
            Document doc = createDocument(999L, 1, "텍스트");
            ChatClientResponse response = createResponseWithDocs(List.of(doc));

            given(documentMetadataManager.findMapByIds(Set.of(999L)))
                    .willReturn(Map.of());

            List<ReferenceResponse> result = referenceExtractor.extract(response);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("source_id가 숫자가 아니면 건너뛴다")
        void extract_InvalidSourceId_Skipped() {
            Document doc = new Document("텍스트", Map.of("source_id", "not_a_number"));
            ChatClientResponse response = createResponseWithDocs(List.of(doc));

            List<ReferenceResponse> result = referenceExtractor.extract(response);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("page_number가 없으면 null로 설정된다")
        void extract_NoPageNumber_SetsNull() {
            Document doc = createDocument(1L, null, "텍스트");
            ChatClientResponse response = createResponseWithDocs(List.of(doc));

            DocumentMetadata metadata = mockDocumentMetadata(1L, "문서A", "pdf", "v1.0.0");
            given(documentMetadataManager.findMapByIds(Set.of(1L)))
                    .willReturn(Map.of(1L, metadata));

            List<ReferenceResponse> result = referenceExtractor.extract(response);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).pageNumber()).isNull();
        }

        @Test
        @DisplayName("텍스트가 null이면 빈 문자열로 설정된다")
        void extract_NullText_SetsEmptyString() {
            Document doc = createDocument(1L, 1, null);
            ChatClientResponse response = createResponseWithDocs(List.of(doc));

            DocumentMetadata metadata = mockDocumentMetadata(1L, "문서A", "pdf", "v1.0.0");
            given(documentMetadataManager.findMapByIds(Set.of(1L)))
                    .willReturn(Map.of(1L, metadata));

            List<ReferenceResponse> result = referenceExtractor.extract(response);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).chunkText()).isEmpty();
        }
    }
}

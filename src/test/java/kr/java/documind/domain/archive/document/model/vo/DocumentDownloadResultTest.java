package kr.java.documind.domain.archive.document.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

@DisplayName("DocumentDownloadResult")
class DocumentDownloadResultTest {

    private DocumentMetadata mockMetadata(String documentName, String extension) {
        DocumentMetadata metadata = mock(DocumentMetadata.class);
        given(metadata.getDocumentName()).willReturn(documentName);
        given(metadata.getExtension()).willReturn(extension);
        return metadata;
    }

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("파일명이 documentName.extension 형식으로 생성된다")
        void of_CreatesCorrectFilename() {
            Resource resource = new ByteArrayResource(new byte[] {1});
            DocumentMetadata metadata = mockMetadata("문서이름", "pdf");

            DocumentDownloadResult result = DocumentDownloadResult.of(resource, metadata);

            assertThat(result.downloadFilename()).isEqualTo("문서이름.pdf");
        }

        @Test
        @DisplayName("resource가 그대로 전달된다")
        void of_PassesThroughResource() {
            Resource resource = new ByteArrayResource(new byte[] {1, 2, 3});
            DocumentMetadata metadata = mockMetadata("doc", "pdf");

            DocumentDownloadResult result = DocumentDownloadResult.of(resource, metadata);

            assertThat(result.resource()).isEqualTo(resource);
        }
    }

    @Nested
    @DisplayName("resolveContentType")
    class ResolveContentType {

        @Test
        @DisplayName("PDF 확장자이면 application/pdf를 반환한다")
        void resolveContentType_Pdf() {
            Resource resource = new ByteArrayResource(new byte[] {1});
            DocumentMetadata metadata = mockMetadata("doc", "pdf");

            DocumentDownloadResult result = DocumentDownloadResult.of(resource, metadata);

            assertThat(result.contentType()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("DOCX 확장자이면 올바른 MIME 타입을 반환한다")
        void resolveContentType_Docx() {
            Resource resource = new ByteArrayResource(new byte[] {1});
            DocumentMetadata metadata = mockMetadata("doc", "docx");

            DocumentDownloadResult result = DocumentDownloadResult.of(resource, metadata);

            assertThat(result.contentType())
                    .isEqualTo(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }

        @Test
        @DisplayName("XLSX 확장자이면 올바른 MIME 타입을 반환한다")
        void resolveContentType_Xlsx() {
            Resource resource = new ByteArrayResource(new byte[] {1});
            DocumentMetadata metadata = mockMetadata("doc", "xlsx");

            DocumentDownloadResult result = DocumentDownloadResult.of(resource, metadata);

            assertThat(result.contentType())
                    .isEqualTo(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }

        @Test
        @DisplayName("JPG 확장자이면 image/jpeg를 반환한다")
        void resolveContentType_Jpg() {
            Resource resource = new ByteArrayResource(new byte[] {1});
            DocumentMetadata metadata = mockMetadata("doc", "jpg");

            DocumentDownloadResult result = DocumentDownloadResult.of(resource, metadata);

            assertThat(result.contentType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("PNG 확장자이면 image/png를 반환한다")
        void resolveContentType_Png() {
            Resource resource = new ByteArrayResource(new byte[] {1});
            DocumentMetadata metadata = mockMetadata("doc", "png");

            DocumentDownloadResult result = DocumentDownloadResult.of(resource, metadata);

            assertThat(result.contentType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("TXT 확장자이면 text/plain을 반환한다")
        void resolveContentType_Txt() {
            Resource resource = new ByteArrayResource(new byte[] {1});
            DocumentMetadata metadata = mockMetadata("doc", "txt");

            DocumentDownloadResult result = DocumentDownloadResult.of(resource, metadata);

            assertThat(result.contentType()).isEqualTo("text/plain");
        }

        @Test
        @DisplayName("알 수 없는 확장자이면 application/octet-stream을 반환한다")
        void resolveContentType_UnknownExtension_ReturnsDefault() {
            Resource resource = new ByteArrayResource(new byte[] {1});
            DocumentMetadata metadata = mockMetadata("doc", "xyz");

            DocumentDownloadResult result = DocumentDownloadResult.of(resource, metadata);

            assertThat(result.contentType()).isEqualTo("application/octet-stream");
        }

        @Test
        @DisplayName("null 확장자이면 application/octet-stream을 반환한다")
        void resolveContentType_NullExtension_ReturnsDefault() {
            Resource resource = new ByteArrayResource(new byte[] {1});
            DocumentMetadata metadata = mockMetadata("doc", null);

            DocumentDownloadResult result = DocumentDownloadResult.of(resource, metadata);

            assertThat(result.contentType()).isEqualTo("application/octet-stream");
        }
    }
}

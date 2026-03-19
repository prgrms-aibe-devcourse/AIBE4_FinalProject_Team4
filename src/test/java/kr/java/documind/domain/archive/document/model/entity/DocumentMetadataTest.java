package kr.java.documind.domain.archive.document.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;
import kr.java.documind.global.entity.DomainSource;
import kr.java.documind.global.enums.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DocumentMetadata")
class DocumentMetadataTest {

    private final UUID projectId = UUID.randomUUID();

    private DocumentGroup createGroup() {
        return DocumentGroup.create(projectId, "개발", "그룹A", "ㄱㄹA");
    }

    private DocumentMetadata createMetadata() {
        return DocumentMetadata.create(
                DomainSource.create(SourceType.DOCUMENT),
                createGroup(),
                "testDoc",
                "pdf",
                1,
                0,
                0,
                "abc123hash",
                1024L,
                "stored/key",
                false,
                EmbeddingStatus.NONE,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("정적 팩토리로 생성 시 모든 필드가 설정된다")
        void create_ValidInput_SetsAllFields() {
            // Given
            DocumentGroup group = createGroup();
            DomainSource domainSource = DomainSource.create(SourceType.DOCUMENT);
            OffsetDateTime uploadedAt = OffsetDateTime.now(ZoneOffset.UTC);

            // When
            DocumentMetadata metadata =
                    DocumentMetadata.create(
                            domainSource,
                            group,
                            "문서이름",
                            "pdf",
                            2,
                            1,
                            3,
                            "hashValue",
                            2048L,
                            "stored/test-key",
                            true,
                            EmbeddingStatus.PENDING,
                            uploadedAt);

            // Then
            assertThat(metadata.getDomainSource()).isEqualTo(domainSource);
            assertThat(metadata.getDocumentGroup()).isEqualTo(group);
            assertThat(metadata.getDocumentName()).isEqualTo("문서이름");
            assertThat(metadata.getExtension()).isEqualTo("pdf");
            assertThat(metadata.getMajorVersion()).isEqualTo(2);
            assertThat(metadata.getMinorVersion()).isEqualTo(1);
            assertThat(metadata.getPatchVersion()).isEqualTo(3);
            assertThat(metadata.getHash()).isEqualTo("hashValue");
            assertThat(metadata.getSize()).isEqualTo(2048L);
            assertThat(metadata.getStoredKey()).isEqualTo("stored/test-key");
            assertThat(metadata.isProcessed()).isTrue();
            assertThat(metadata.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.PENDING);
            assertThat(metadata.getUploadedAt()).isEqualTo(uploadedAt);
        }

        @Test
        @DisplayName("초성이 자동 설정된다")
        void create_SetsChoseong() {
            DocumentMetadata metadata = createMetadata();

            assertThat(metadata.getChoseong()).isNotNull();
            assertThat(metadata.getChoseong()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("getVersionString")
    class GetVersionString {

        @Test
        @DisplayName("v{major}.{minor}.{patch} 형식으로 반환한다")
        void getVersionString_ReturnsFormattedString() {
            DocumentMetadata metadata = createMetadata();

            assertThat(metadata.getVersionString()).isEqualTo("v1.0.0");
        }

        @Test
        @DisplayName("버전 변경 후 반영된 문자열을 반환한다")
        void getVersionString_AfterUpdate_ReturnsUpdatedString() {
            DocumentMetadata metadata = createMetadata();
            metadata.updateVersion(3, 2, 1);

            assertThat(metadata.getVersionString()).isEqualTo("v3.2.1");
        }
    }

    @Nested
    @DisplayName("updateVersion")
    class UpdateVersion {

        @Test
        @DisplayName("버전 필드가 모두 갱신된다")
        void updateVersion_UpdatesAllVersionFields() {
            DocumentMetadata metadata = createMetadata();

            metadata.updateVersion(5, 3, 7);

            assertThat(metadata.getMajorVersion()).isEqualTo(5);
            assertThat(metadata.getMinorVersion()).isEqualTo(3);
            assertThat(metadata.getPatchVersion()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("updateFile")
    class UpdateFile {

        @Test
        @DisplayName("파일 관련 필드가 모두 갱신된다")
        void updateFile_UpdatesAllFileFields() {
            DocumentMetadata metadata = createMetadata();

            metadata.updateFile("newDoc", "docx", "newHash", 4096L, "stored/new-key");

            assertThat(metadata.getDocumentName()).isEqualTo("newDoc");
            assertThat(metadata.getExtension()).isEqualTo("docx");
            assertThat(metadata.getHash()).isEqualTo("newHash");
            assertThat(metadata.getSize()).isEqualTo(4096L);
            assertThat(metadata.getStoredKey()).isEqualTo("stored/new-key");
        }

        @Test
        @DisplayName("초성이 재설정된다")
        void updateFile_UpdatesChoseong() {
            DocumentMetadata metadata = createMetadata();
            String originalChoseong = metadata.getChoseong();

            metadata.updateFile("새문서", "pdf", "newHash", 2048L, "stored/new-key");

            assertThat(metadata.getChoseong()).isNotNull();
            assertThat(metadata.getChoseong()).isNotEqualTo(originalChoseong);
        }
    }

    @Nested
    @DisplayName("changeProcessed")
    class ChangeProcessed {

        @Test
        @DisplayName("false에서 true로 변경")
        void changeProcessed_FalseToTrue() {
            DocumentMetadata metadata = createMetadata();
            assertThat(metadata.isProcessed()).isFalse();

            metadata.changeProcessed(true);

            assertThat(metadata.isProcessed()).isTrue();
        }

        @Test
        @DisplayName("true에서 false로 변경")
        void changeProcessed_TrueToFalse() {
            DocumentMetadata metadata = createMetadata();
            metadata.changeProcessed(true);

            metadata.changeProcessed(false);

            assertThat(metadata.isProcessed()).isFalse();
        }
    }

    @Nested
    @DisplayName("changeEmbeddingStatus")
    class ChangeEmbeddingStatus {

        @Test
        @DisplayName("NONE에서 PENDING으로 변경")
        void changeEmbeddingStatus_NoneToPending() {
            DocumentMetadata metadata = createMetadata();

            metadata.changeEmbeddingStatus(EmbeddingStatus.PENDING);

            assertThat(metadata.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.PENDING);
        }

        @Test
        @DisplayName("PENDING에서 SUCCESS로 변경")
        void changeEmbeddingStatus_PendingToSuccess() {
            DocumentMetadata metadata = createMetadata();
            metadata.changeEmbeddingStatus(EmbeddingStatus.PENDING);

            metadata.changeEmbeddingStatus(EmbeddingStatus.SUCCESS);

            assertThat(metadata.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.SUCCESS);
        }
    }

    @Nested
    @DisplayName("markModified")
    class MarkModified {

        @Test
        @DisplayName("reuploadedAt이 UTC로 설정된다")
        void markModified_SetsReuploadedAtWithUtc() {
            DocumentMetadata metadata = createMetadata();
            assertThat(metadata.getReuploadedAt()).isNull();

            OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
            metadata.markModified();
            OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);

            assertThat(metadata.getReuploadedAt()).isNotNull();
            assertThat(metadata.getReuploadedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
            assertThat(metadata.getReuploadedAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("여러 번 호출 시 마지막 시각으로 갱신된다")
        void markModified_CalledMultipleTimes_UpdatesToLatest() {
            DocumentMetadata metadata = createMetadata();

            metadata.markModified();
            OffsetDateTime first = metadata.getReuploadedAt();

            metadata.markModified();
            OffsetDateTime second = metadata.getReuploadedAt();

            assertThat(second).isAfterOrEqualTo(first);
        }
    }
}

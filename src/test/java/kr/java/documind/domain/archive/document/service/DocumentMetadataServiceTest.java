package kr.java.documind.domain.archive.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.request.NewVersionDocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentDetailResponse;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentDownloadResult;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentMetadataResponse;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.archive.document.model.repository.DocumentGroupRepository;
import kr.java.documind.domain.archive.document.model.repository.DocumentMetadataRepository;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import kr.java.documind.global.storage.FileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentMetadataServiceTest {

    private DocumentMetadataService documentMetadataService;

    @Mock private DocumentGroupRepository documentGroupRepository;
    @Mock private DocumentMetadataRepository documentMetadataRepository;
    @Mock private FileStore fileStore;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final Long DOCUMENT_ID = 1L;
    private static final Long GROUP_ID = 1L;

    @BeforeEach
    void setUp() {
        documentMetadataService =
                new DocumentMetadataService(
                        documentGroupRepository, documentMetadataRepository, fileStore);
    }

    private DocumentGroup createGroup() {
        return DocumentGroup.create(PROJECT_ID, "기술", "설계문서", "");
    }

    private DocumentMetadata createMetadata(DocumentGroup group) {
        return DocumentMetadata.create(
                group,
                "document",
                "",
                "pdf",
                1,
                0,
                0,
                "abc123hash",
                1024L,
                "stored-key",
                LocalDateTime.of(2025, 1, 1, 0, 0));
    }

    private MultipartFile mockFile(String filename) {
        MultipartFile file = mock(MultipartFile.class);
        given(file.isEmpty()).willReturn(false);
        given(file.getOriginalFilename()).willReturn(filename);
        return file;
    }

    @Nested
    @DisplayName("getDocumentDetail")
    class GetDocumentDetail {

        @Test
        @DisplayName("문서 상세 정보를 조회한다")
        void returnsDocumentDetail() {
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(documentMetadataRepository.findById(DOCUMENT_ID))
                    .willReturn(Optional.of(metadata));
            given(
                            documentMetadataRepository
                                    .findByDocumentGroupOrderByMajorVersionDescMinorVersionDescPatchVersionDesc(
                                            group))
                    .willReturn(List.of(metadata));

            DocumentDetailResponse result = documentMetadataService.getDocumentDetail(DOCUMENT_ID);

            assertThat(result.documentName()).isEqualTo("document");
            assertThat(result.groupName()).isEqualTo("설계문서");
            assertThat(result.versions()).hasSize(1);
        }

        @Test
        @DisplayName("존재하지 않는 문서면 NotFoundException을 던진다")
        void throwsNotFoundExceptionWhenDocumentNotExists() {
            given(documentMetadataRepository.findById(DOCUMENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> documentMetadataService.getDocumentDetail(DOCUMENT_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(DOCUMENT_ID));
        }
    }

    @Nested
    @DisplayName("downloadDocument")
    class DownloadDocument {

        @Test
        @DisplayName("파일 리소스를 반환한다")
        void returnsFileResource() {
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            Resource resource = mock(Resource.class);

            given(documentMetadataRepository.findById(DOCUMENT_ID))
                    .willReturn(Optional.of(metadata));
            given(fileStore.load("stored-key")).willReturn(resource);

            DocumentDownloadResult result = documentMetadataService.downloadDocument(DOCUMENT_ID);

            assertThat(result.resource()).isEqualTo(resource);
            assertThat(result.downloadFilename()).isEqualTo("document.pdf");
        }

        @Test
        @DisplayName("존재하지 않는 문서면 NotFoundException을 던진다")
        void throwsNotFoundExceptionWhenDocumentNotExists() {
            given(documentMetadataRepository.findById(DOCUMENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> documentMetadataService.downloadDocument(DOCUMENT_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(DOCUMENT_ID));
        }
    }

    @Nested
    @DisplayName("uploadDocument")
    class UploadDocument {

        @Test
        @DisplayName("문서를 업로드하고 응답을 반환한다")
        void uploadsDocumentAndReturnsResponse() throws IOException {
            MultipartFile file = mockFile("document.pdf");
            given(file.getSize()).willReturn(2048L);
            DocumentUploadRequest request = new DocumentUploadRequest("설계문서", "기술", 1, 0, 0);
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(
                            documentGroupRepository.existsByProjectIdAndCategoryAndGroupName(
                                    PROJECT_ID, "기술", "설계문서"))
                    .willReturn(false);
            given(documentGroupRepository.save(any(DocumentGroup.class))).willReturn(group);
            given(fileStore.save(file)).willReturn("new-stored-key");
            given(documentMetadataRepository.existsByProjectIdAndHash(any(), any()))
                    .willReturn(false);
            given(documentMetadataRepository.save(any(DocumentMetadata.class)))
                    .willReturn(metadata);

            try (MockedStatic<kr.java.documind.global.util.FileUtil> fileUtil =
                    Mockito.mockStatic(kr.java.documind.global.util.FileUtil.class)) {
                fileUtil.when(() -> kr.java.documind.global.util.FileUtil.computeSha256(file))
                        .thenReturn("newhash123");

                DocumentMetadataResponse result =
                        documentMetadataService.uploadDocument(PROJECT_ID, request, file);

                assertThat(result).isNotNull();
                then(fileStore).should().save(file);
                then(fileStore).should().registerRollback("new-stored-key");
            }
        }

        @Test
        @DisplayName("빈 파일이면 BadRequestException을 던진다")
        void throwsBadRequestExceptionWhenFileIsEmpty() {
            MultipartFile file = mock(MultipartFile.class);
            given(file.isEmpty()).willReturn(true);
            DocumentUploadRequest request = new DocumentUploadRequest("설계문서", "기술", 1, 0, 0);

            assertThatThrownBy(
                            () -> documentMetadataService.uploadDocument(PROJECT_ID, request, file))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("비어있거나");
        }

        @Test
        @DisplayName("중복 그룹(카테고리+그룹명)이면 ConflictException을 던진다")
        void throwsConflictExceptionWhenDuplicateGroup() {
            MultipartFile file = mockFile("document.pdf");
            DocumentUploadRequest request = new DocumentUploadRequest("설계문서", "기술", 1, 0, 0);

            given(
                            documentGroupRepository.existsByProjectIdAndCategoryAndGroupName(
                                    PROJECT_ID, "기술", "설계문서"))
                    .willReturn(true);

            assertThatThrownBy(
                            () -> documentMetadataService.uploadDocument(PROJECT_ID, request, file))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("이미 존재");
        }

        @Test
        @DisplayName("동일 해시 파일 존재 시 ConflictException을 던진다")
        void throwsConflictExceptionWhenDuplicateHash() throws IOException {
            MultipartFile file = mockFile("document.pdf");
            DocumentUploadRequest request = new DocumentUploadRequest("설계문서", "기술", 1, 0, 0);
            DocumentGroup group = createGroup();

            given(
                            documentGroupRepository.existsByProjectIdAndCategoryAndGroupName(
                                    PROJECT_ID, "기술", "설계문서"))
                    .willReturn(false);
            given(documentGroupRepository.save(any(DocumentGroup.class))).willReturn(group);
            given(fileStore.save(file)).willReturn("new-stored-key");
            given(documentMetadataRepository.existsByProjectIdAndHash(PROJECT_ID, "duphash"))
                    .willReturn(true);

            try (MockedStatic<kr.java.documind.global.util.FileUtil> fileUtil =
                    Mockito.mockStatic(kr.java.documind.global.util.FileUtil.class)) {
                fileUtil.when(() -> kr.java.documind.global.util.FileUtil.computeSha256(file))
                        .thenReturn("duphash");

                assertThatThrownBy(
                                () ->
                                        documentMetadataService.uploadDocument(
                                                PROJECT_ID, request, file))
                        .isInstanceOf(ConflictException.class)
                        .hasMessageContaining("동일한 내용");
            }
        }
    }

    @Nested
    @DisplayName("updateDocument")
    class UpdateDocument {

        @Test
        @DisplayName("버전만 변경한다")
        void updatesVersionOnly() {
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            DocumentUpdateRequest request = new DocumentUpdateRequest(2, 0, 0);

            given(documentMetadataRepository.findById(DOCUMENT_ID))
                    .willReturn(Optional.of(metadata));
            given(
                            documentMetadataRepository
                                    .existsByDocumentGroupAndMajorVersionAndMinorVersionAndPatchVersion(
                                            group, 2, 0, 0))
                    .willReturn(false);

            documentMetadataService.updateDocument(DOCUMENT_ID, request, null);

            assertThat(metadata.getMajorVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("파일만 변경한다")
        void updatesFileOnly() throws IOException {
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            // same version as current (1, 0, 0)
            DocumentUpdateRequest request = new DocumentUpdateRequest(1, 0, 0);
            MultipartFile file = mockFile("newdoc.pdf");
            given(file.getSize()).willReturn(2048L);

            given(documentMetadataRepository.findById(DOCUMENT_ID))
                    .willReturn(Optional.of(metadata));
            given(fileStore.save(file)).willReturn("new-key");
            given(documentMetadataRepository.existsByProjectIdAndHash(PROJECT_ID, "newhash"))
                    .willReturn(false);

            try (MockedStatic<kr.java.documind.global.util.FileUtil> fileUtil =
                    Mockito.mockStatic(kr.java.documind.global.util.FileUtil.class)) {
                fileUtil.when(() -> kr.java.documind.global.util.FileUtil.computeSha256(file))
                        .thenReturn("newhash");

                documentMetadataService.updateDocument(DOCUMENT_ID, request, file);

                then(fileStore).should().save(file);
                then(fileStore).should().delete("stored-key");
                assertThat(metadata.getStoredKey()).isEqualTo("new-key");
            }
        }

        @Test
        @DisplayName("버전과 파일 모두 동일하면 ConflictException을 던진다")
        void throwsConflictExceptionWhenNothingChanged() {
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            // same version
            DocumentUpdateRequest request = new DocumentUpdateRequest(1, 0, 0);

            given(documentMetadataRepository.findById(DOCUMENT_ID))
                    .willReturn(Optional.of(metadata));

            // no file provided → fileChanged=false, version same → versionChanged=false
            assertThatThrownBy(
                            () ->
                                    documentMetadataService.updateDocument(
                                            DOCUMENT_ID, request, null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("동일");
        }

        @Test
        @DisplayName("중복 버전이면 ConflictException을 던진다")
        void throwsConflictExceptionWhenDuplicateVersion() {
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            DocumentUpdateRequest request = new DocumentUpdateRequest(2, 0, 0);

            given(documentMetadataRepository.findById(DOCUMENT_ID))
                    .willReturn(Optional.of(metadata));
            given(
                            documentMetadataRepository
                                    .existsByDocumentGroupAndMajorVersionAndMinorVersionAndPatchVersion(
                                            group, 2, 0, 0))
                    .willReturn(true);

            assertThatThrownBy(
                            () ->
                                    documentMetadataService.updateDocument(
                                            DOCUMENT_ID, request, null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("이미 존재하는 버전");
        }

        @Test
        @DisplayName("동일 해시 파일 존재 시 ConflictException을 던진다")
        void throwsConflictExceptionWhenDuplicateHash() throws IOException {
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            DocumentUpdateRequest request = new DocumentUpdateRequest(1, 0, 0);
            MultipartFile file = mock(MultipartFile.class);
            given(file.isEmpty()).willReturn(false);

            given(documentMetadataRepository.findById(DOCUMENT_ID))
                    .willReturn(Optional.of(metadata));
            given(documentMetadataRepository.existsByProjectIdAndHash(PROJECT_ID, "duphash"))
                    .willReturn(true);

            try (MockedStatic<kr.java.documind.global.util.FileUtil> fileUtil =
                    Mockito.mockStatic(kr.java.documind.global.util.FileUtil.class)) {
                fileUtil.when(() -> kr.java.documind.global.util.FileUtil.computeSha256(file))
                        .thenReturn("duphash");

                assertThatThrownBy(
                                () ->
                                        documentMetadataService.updateDocument(
                                                DOCUMENT_ID, request, file))
                        .isInstanceOf(ConflictException.class)
                        .hasMessageContaining("동일한 내용");
            }
        }
    }

    @Nested
    @DisplayName("deleteDocument")
    class DeleteDocument {

        @Test
        @DisplayName("문서를 삭제한다")
        void deletesDocument() {
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(documentMetadataRepository.findById(DOCUMENT_ID))
                    .willReturn(Optional.of(metadata));
            given(documentMetadataRepository.countByDocumentGroup(group)).willReturn(1L);

            documentMetadataService.deleteDocument(DOCUMENT_ID);

            then(documentMetadataRepository).should().delete(metadata);
            then(fileStore).should().delete("stored-key");
        }

        @Test
        @DisplayName("마지막 문서면 그룹도 함께 삭제한다")
        void deletesGroupWhenLastDocument() {
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(documentMetadataRepository.findById(DOCUMENT_ID))
                    .willReturn(Optional.of(metadata));
            given(documentMetadataRepository.countByDocumentGroup(group)).willReturn(0L);

            documentMetadataService.deleteDocument(DOCUMENT_ID);

            then(documentMetadataRepository).should().delete(metadata);
            then(documentGroupRepository).should().delete(group);
        }

        @Test
        @DisplayName("문서가 남아있으면 그룹은 삭제하지 않는다")
        void doesNotDeleteGroupWhenDocumentsRemain() {
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(documentMetadataRepository.findById(DOCUMENT_ID))
                    .willReturn(Optional.of(metadata));
            given(documentMetadataRepository.countByDocumentGroup(group)).willReturn(2L);

            documentMetadataService.deleteDocument(DOCUMENT_ID);

            then(documentGroupRepository).should(never()).delete(any());
        }

        @Test
        @DisplayName("존재하지 않는 문서면 NotFoundException을 던진다")
        void throwsNotFoundExceptionWhenDocumentNotExists() {
            given(documentMetadataRepository.findById(DOCUMENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> documentMetadataService.deleteDocument(DOCUMENT_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(DOCUMENT_ID));
        }
    }

    @Nested
    @DisplayName("uploadNewVersion")
    class UploadNewVersion {

        @Test
        @DisplayName("새 버전을 업로드하고 응답을 반환한다")
        void uploadsNewVersionAndReturnsResponse() throws IOException {
            MultipartFile file = mockFile("document.pdf");
            given(file.getSize()).willReturn(2048L);
            NewVersionDocumentUploadRequest request = new NewVersionDocumentUploadRequest(2, 0, 0);
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(documentGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
            given(
                            documentMetadataRepository
                                    .existsByDocumentGroupAndMajorVersionAndMinorVersionAndPatchVersion(
                                            group, 2, 0, 0))
                    .willReturn(false);
            given(fileStore.save(file)).willReturn("new-stored-key");
            given(documentMetadataRepository.existsByProjectIdAndHash(any(), any()))
                    .willReturn(false);
            given(documentMetadataRepository.save(any(DocumentMetadata.class)))
                    .willReturn(metadata);

            try (MockedStatic<kr.java.documind.global.util.FileUtil> fileUtil =
                    Mockito.mockStatic(kr.java.documind.global.util.FileUtil.class)) {
                fileUtil.when(() -> kr.java.documind.global.util.FileUtil.computeSha256(file))
                        .thenReturn("newhash123");

                DocumentMetadataResponse result =
                        documentMetadataService.uploadNewVersion(GROUP_ID, request, file);

                assertThat(result).isNotNull();
                then(fileStore).should().save(file);
            }
        }

        @Test
        @DisplayName("빈 파일이면 BadRequestException을 던진다")
        void throwsBadRequestExceptionWhenFileIsEmpty() {
            MultipartFile file = mock(MultipartFile.class);
            given(file.isEmpty()).willReturn(true);
            NewVersionDocumentUploadRequest request = new NewVersionDocumentUploadRequest(2, 0, 0);

            assertThatThrownBy(
                            () -> documentMetadataService.uploadNewVersion(GROUP_ID, request, file))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("비어있거나");
        }

        @Test
        @DisplayName("중복 버전이면 ConflictException을 던진다")
        void throwsConflictExceptionWhenDuplicateVersion() {
            MultipartFile file = mockFile("document.pdf");
            NewVersionDocumentUploadRequest request = new NewVersionDocumentUploadRequest(1, 0, 0);
            DocumentGroup group = createGroup();

            given(documentGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
            given(
                            documentMetadataRepository
                                    .existsByDocumentGroupAndMajorVersionAndMinorVersionAndPatchVersion(
                                            group, 1, 0, 0))
                    .willReturn(true);

            assertThatThrownBy(
                            () -> documentMetadataService.uploadNewVersion(GROUP_ID, request, file))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("이미 존재하는 버전");
        }

        @Test
        @DisplayName("존재하지 않는 그룹이면 NotFoundException을 던진다")
        void throwsNotFoundExceptionWhenGroupNotExists() {
            MultipartFile file = mockFile("document.pdf");
            NewVersionDocumentUploadRequest request = new NewVersionDocumentUploadRequest(2, 0, 0);

            given(documentGroupRepository.findById(GROUP_ID)).willReturn(Optional.empty());

            assertThatThrownBy(
                            () -> documentMetadataService.uploadNewVersion(GROUP_ID, request, file))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(GROUP_ID));
        }
    }
}

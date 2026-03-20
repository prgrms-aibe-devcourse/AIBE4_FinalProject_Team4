package kr.java.documind.domain.archive.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.document.event.DocumentVectorEventPublisher;
import kr.java.documind.domain.archive.document.infrastructure.DocumentFileStorage;
import kr.java.documind.domain.archive.document.infrastructure.DocumentGroupManager;
import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.DocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.dto.request.NewVersionDocumentUploadRequest;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;
import kr.java.documind.global.entity.DomainSource;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentMetadataService")
class DocumentMetadataServiceTest {

    @Mock private DocumentGroupManager documentGroupManager;
    @Mock private DocumentMetadataManager documentMetadataManager;
    @Mock private DocumentFileStorage documentFileStorage;
    @Mock private DocumentVectorEventPublisher documentVectorEventPublisher;
    @InjectMocks private DocumentMetadataService documentMetadataService;

    private final UUID projectId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final String publicId = "test-public-id";
    private final Long documentId = 1L;
    private final Long groupId = 1L;

    private DocumentGroup createGroup() {
        return DocumentGroup.create(projectId, "개발", "그룹A", "ㄱㄹA");
    }

    private DocumentMetadata createMetadata(DocumentGroup group) {
        DomainSource domainSource = DomainSource.create(SourceType.DOCUMENT);
        return DocumentMetadata.create(
                domainSource,
                group,
                "testDoc",
                "ㅌㅅㄷ",
                "pdf",
                1,
                0,
                0,
                "abc123hash",
                1024L,
                "stored/key",
                EmbeddingStatus.NONE,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private MultipartFile mockFile(String filename) {
        MultipartFile file = mock(MultipartFile.class);
        given(file.isEmpty()).willReturn(false);
        given(file.getOriginalFilename()).willReturn(filename);
        return file;
    }

    private DocumentFileStorage.StoredDocumentFile storedFile() {
        return new DocumentFileStorage.StoredDocumentFile(
                "testDoc", "pdf", 1024L, "stored/new-key");
    }

    @Nested
    @DisplayName("문서 상세 조회")
    class GetDocumentDetail {

        @Test
        @DisplayName("정상 조회 시 DocumentDetailResponse 반환")
        void getDocumentDetail_ValidDocument_ReturnsDetail() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);
            given(documentMetadataManager.findVersionsByGroup(group)).willReturn(List.of(metadata));

            // When
            var result = documentMetadataService.getDocumentDetail(projectId, documentId);

            // Then
            assertThat(result.documentName()).isEqualTo("testDoc");
            assertThat(result.groupName()).isEqualTo("그룹A");
            assertThat(result.category()).isEqualTo("개발");
            assertThat(result.versions()).hasSize(1);
        }

        @Test
        @DisplayName("존재하지 않는 문서 시 NotFoundException")
        void getDocumentDetail_DocumentNotFound_ThrowsNotFoundException() {
            // Given
            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willThrow(new NotFoundException("프로젝트에서 문서를 찾을 수 없습니다."));

            // When & Then
            assertThatThrownBy(
                            () -> documentMetadataService.getDocumentDetail(projectId, documentId))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("임베딩 상태 조회")
    class GetEmbeddingStatus {

        @Test
        @DisplayName("정상 조회 시 EmbeddingStatus 반환")
        void getEmbeddingStatus_ValidDocument_ReturnsStatus() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);

            // When
            var result = documentMetadataService.getEmbeddingStatus(projectId, documentId);

            // Then
            assertThat(result).isEqualTo(EmbeddingStatus.NONE);
        }

        @Test
        @DisplayName("존재하지 않는 문서 시 NotFoundException")
        void getEmbeddingStatus_DocumentNotFound_ThrowsNotFoundException() {
            // Given
            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willThrow(new NotFoundException("프로젝트에서 문서를 찾을 수 없습니다."));

            // When & Then
            assertThatThrownBy(
                            () -> documentMetadataService.getEmbeddingStatus(projectId, documentId))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("문서 다운로드")
    class DownloadDocument {

        @Test
        @DisplayName("정상 다운로드 시 DocumentDownloadResult 반환")
        void downloadDocument_ValidDocument_ReturnsResult() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            Resource resource = new ByteArrayResource(new byte[] {1, 2, 3});

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);
            given(documentFileStorage.load("stored/key")).willReturn(resource);

            // When
            var result = documentMetadataService.downloadDocument(projectId, documentId);

            // Then
            assertThat(result.resource()).isEqualTo(resource);
            assertThat(result.downloadFilename()).isEqualTo("testDoc.pdf");
        }

        @Test
        @DisplayName("존재하지 않는 문서 시 NotFoundException")
        void downloadDocument_DocumentNotFound_ThrowsNotFoundException() {
            // Given
            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willThrow(new NotFoundException("프로젝트에서 문서를 찾을 수 없습니다."));

            // When & Then
            assertThatThrownBy(
                            () -> documentMetadataService.downloadDocument(projectId, documentId))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("새 그룹으로 문서 업로드")
    class UploadDocumentWithNewGroup {

        @Test
        @DisplayName("정상 업로드 시 그룹 + 메타데이터 생성")
        void uploadDocumentWithNewGroup_ValidRequest_CreatesGroupAndMetadata() {
            // Given
            MultipartFile file = mockFile("testDoc.pdf");
            DocumentUploadRequest request = new DocumentUploadRequest("그룹A", "개발", 1, 0, 0, true);
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(documentGroupManager.createGroup(projectId, "개발", "그룹A")).willReturn(group);
            given(documentGroupManager.save(group)).willReturn(group);
            given(documentFileStorage.computeHash(file)).willReturn("newHash");
            given(documentMetadataManager.existsByProjectIdAndHash(projectId, "newHash"))
                    .willReturn(false);
            given(documentFileStorage.store(file)).willReturn(storedFile());
            given(
                            documentMetadataManager.createMetadata(
                                    eq(group), any(), any(), anyInt(), anyInt(), anyInt(), any(),
                                    anyLong(), any(), any(), any()))
                    .willReturn(metadata);

            // When
            var result =
                    documentMetadataService.uploadDocumentWithNewGroup(
                            publicId, projectId, memberId, request, file);

            // Then
            assertThat(result.documentName()).isEqualTo("testDoc");
            then(documentGroupManager).should().validateGroupNameUniqueness(projectId, "개발", "그룹A");
            then(documentGroupManager).should().save(group);
            then(documentVectorEventPublisher)
                    .should()
                    .createEvent(eq(publicId), eq(projectId), eq(memberId), eq(metadata), eq(true));
        }

        @Test
        @DisplayName("그룹명 중복 시 ConflictException")
        void uploadDocumentWithNewGroup_DuplicateGroupName_ThrowsConflictException() {
            // Given
            MultipartFile file = mockFile("testDoc.pdf");
            DocumentUploadRequest request = new DocumentUploadRequest("그룹A", "개발", 1, 0, 0, null);

            doThrow(new ConflictException("문서 그룹명(그룹A)이 카테고리(개발)에 이미 존재합니다."))
                    .when(documentGroupManager)
                    .validateGroupNameUniqueness(projectId, "개발", "그룹A");

            // When & Then
            assertThatThrownBy(
                            () ->
                                    documentMetadataService.uploadDocumentWithNewGroup(
                                            publicId, projectId, memberId, request, file))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("해시 중복 시 ConflictException")
        void uploadDocumentWithNewGroup_DuplicateHash_ThrowsConflictException() {
            // Given
            MultipartFile file = mockFile("testDoc.pdf");
            DocumentUploadRequest request = new DocumentUploadRequest("그룹B", "개발", 1, 0, 0, null);
            DocumentGroup group = createGroup();

            given(documentGroupManager.createGroup(projectId, "개발", "그룹B")).willReturn(group);
            given(documentGroupManager.save(group)).willReturn(group);
            given(documentFileStorage.computeHash(file)).willReturn("duplicateHash");
            given(documentMetadataManager.existsByProjectIdAndHash(projectId, "duplicateHash"))
                    .willReturn(true);

            // When & Then
            assertThatThrownBy(
                            () ->
                                    documentMetadataService.uploadDocumentWithNewGroup(
                                            publicId, projectId, memberId, request, file))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("동일한 내용");
        }

        @Test
        @DisplayName("빈 파일 시 BadRequestException")
        void uploadDocumentWithNewGroup_EmptyFile_ThrowsBadRequestException() {
            // Given
            MultipartFile file = mock(MultipartFile.class);
            given(file.isEmpty()).willReturn(true);
            DocumentUploadRequest request = new DocumentUploadRequest("그룹A", "개발", 1, 0, 0, null);

            // When & Then
            assertThatThrownBy(
                            () ->
                                    documentMetadataService.uploadDocumentWithNewGroup(
                                            publicId, projectId, memberId, request, file))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("기존 그룹에 문서 업로드")
    class UploadDocumentToGroup {

        @Test
        @DisplayName("기존 그룹에 새 버전 정상 추가")
        void uploadDocumentToGroup_ValidRequest_AddsNewVersion() {
            // Given
            MultipartFile file = mockFile("testDoc.pdf");
            NewVersionDocumentUploadRequest request =
                    new NewVersionDocumentUploadRequest(2, 0, 0, false);
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);
            given(documentMetadataManager.existsByGroupAndVersion(group, 2, 0, 0))
                    .willReturn(false);
            given(documentFileStorage.computeHash(file)).willReturn("newHash");
            given(documentMetadataManager.existsByProjectIdAndHash(projectId, "newHash"))
                    .willReturn(false);
            given(documentFileStorage.store(file)).willReturn(storedFile());
            given(
                            documentMetadataManager.createMetadata(
                                    eq(group), any(), any(), anyInt(), anyInt(), anyInt(), any(),
                                    anyLong(), any(), any(), any()))
                    .willReturn(metadata);

            // When
            var result =
                    documentMetadataService.uploadDocumentToGroup(
                            publicId, projectId, memberId, groupId, request, file);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.documentName()).isEqualTo("testDoc");
            then(documentVectorEventPublisher)
                    .should()
                    .createEvent(
                            eq(publicId), eq(projectId), eq(memberId), eq(metadata), eq(false));
        }

        @Test
        @DisplayName("존재하지 않는 그룹 시 NotFoundException")
        void uploadDocumentToGroup_GroupNotFound_ThrowsNotFoundException() {
            // Given
            MultipartFile file = mockFile("testDoc.pdf");
            NewVersionDocumentUploadRequest request =
                    new NewVersionDocumentUploadRequest(1, 0, 0, null);

            given(documentGroupManager.getByIdAndProjectId(groupId, projectId))
                    .willThrow(new NotFoundException("프로젝트에서 문서 그룹을 찾을 수 없습니다."));

            // When & Then
            assertThatThrownBy(
                            () ->
                                    documentMetadataService.uploadDocumentToGroup(
                                            publicId, projectId, memberId, groupId, request, file))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("빈 파일 시 BadRequestException")
        void uploadDocumentToGroup_EmptyFile_ThrowsBadRequestException() {
            // Given
            MultipartFile file = mock(MultipartFile.class);
            given(file.isEmpty()).willReturn(true);
            NewVersionDocumentUploadRequest request =
                    new NewVersionDocumentUploadRequest(1, 0, 0, null);

            // When & Then
            assertThatThrownBy(
                            () ->
                                    documentMetadataService.uploadDocumentToGroup(
                                            publicId, projectId, memberId, groupId, request, file))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("해시 중복 시 ConflictException")
        void uploadDocumentToGroup_DuplicateHash_ThrowsConflictException() {
            // Given
            MultipartFile file = mockFile("testDoc.pdf");
            NewVersionDocumentUploadRequest request =
                    new NewVersionDocumentUploadRequest(2, 0, 0, null);
            DocumentGroup group = createGroup();

            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);
            given(documentMetadataManager.existsByGroupAndVersion(group, 2, 0, 0))
                    .willReturn(false);
            given(documentFileStorage.computeHash(file)).willReturn("duplicateHash");
            given(documentMetadataManager.existsByProjectIdAndHash(projectId, "duplicateHash"))
                    .willReturn(true);

            // When & Then
            assertThatThrownBy(
                            () ->
                                    documentMetadataService.uploadDocumentToGroup(
                                            publicId, projectId, memberId, groupId, request, file))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("동일한 내용");
        }

        @Test
        @DisplayName("동일 버전 존재 시 ConflictException")
        void uploadDocumentToGroup_DuplicateVersion_ThrowsConflictException() {
            // Given
            MultipartFile file = mockFile("testDoc.pdf");
            NewVersionDocumentUploadRequest request =
                    new NewVersionDocumentUploadRequest(1, 0, 0, null);
            DocumentGroup group = createGroup();

            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);
            given(documentMetadataManager.existsByGroupAndVersion(group, 1, 0, 0)).willReturn(true);

            // When & Then
            assertThatThrownBy(
                            () ->
                                    documentMetadataService.uploadDocumentToGroup(
                                            publicId, projectId, memberId, groupId, request, file))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("이미 존재하는 버전");
        }
    }

    @Nested
    @DisplayName("문서 수정")
    class UpdateDocument {

        @Test
        @DisplayName("버전만 변경")
        void updateDocument_VersionOnly_UpdatesVersion() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            DocumentUpdateRequest request = new DocumentUpdateRequest(2, 0, 0, false);

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);
            given(documentMetadataManager.existsByGroupAndVersion(group, 2, 0, 0))
                    .willReturn(false);

            // When
            documentMetadataService.updateDocument(
                    publicId, projectId, memberId, documentId, request, null);

            // Then
            assertThat(metadata.getMajorVersion()).isEqualTo(2);
            assertThat(metadata.getMinorVersion()).isEqualTo(0);
            assertThat(metadata.getPatchVersion()).isEqualTo(0);
            then(documentFileStorage).should(never()).replace(any(), any());
        }

        @Test
        @DisplayName("파일만 변경")
        void updateDocument_FileOnly_ReplacesFile() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            MultipartFile file = mockFile("newDoc.pdf");
            DocumentUpdateRequest request = new DocumentUpdateRequest(1, 0, 0, false);

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);
            given(documentFileStorage.computeHash(file)).willReturn("newHash");
            given(documentMetadataManager.existsByProjectIdAndHash(projectId, "newHash"))
                    .willReturn(false);
            given(documentFileStorage.replace("stored/key", file)).willReturn(storedFile());

            // When
            documentMetadataService.updateDocument(
                    publicId, projectId, memberId, documentId, request, file);

            // Then
            then(documentMetadataManager)
                    .should()
                    .updateFile(
                            eq(metadata),
                            eq("testDoc"),
                            eq("pdf"),
                            eq("newHash"),
                            eq(1024L),
                            eq("stored/new-key"));
            then(documentVectorEventPublisher)
                    .should()
                    .replaceEvent(
                            eq(publicId), eq(projectId), eq(memberId), eq(metadata), eq(false));
        }

        @Test
        @DisplayName("복합 변경 (버전 + 파일)")
        void updateDocument_AllChanged_UpdatesAll() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            MultipartFile file = mockFile("newDoc.pdf");
            DocumentUpdateRequest request = new DocumentUpdateRequest(2, 1, 0, true);

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);
            given(documentMetadataManager.existsByGroupAndVersion(group, 2, 1, 0))
                    .willReturn(false);
            given(documentFileStorage.computeHash(file)).willReturn("newHash");
            given(documentMetadataManager.existsByProjectIdAndHash(projectId, "newHash"))
                    .willReturn(false);
            given(documentFileStorage.replace("stored/key", file)).willReturn(storedFile());

            // When
            documentMetadataService.updateDocument(
                    publicId, projectId, memberId, documentId, request, file);

            // Then
            assertThat(metadata.getMajorVersion()).isEqualTo(2);
            assertThat(metadata.getMinorVersion()).isEqualTo(1);
            then(documentMetadataManager)
                    .should()
                    .updateFile(eq(metadata), any(), any(), any(), anyLong(), any());
            then(documentVectorEventPublisher)
                    .should()
                    .replaceEvent(
                            eq(publicId), eq(projectId), eq(memberId), eq(metadata), eq(true));
        }

        @Test
        @DisplayName("아무것도 안 바뀌면 ConflictException")
        void updateDocument_NoChanges_ThrowsConflictException() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            DocumentUpdateRequest request = new DocumentUpdateRequest(1, 0, 0, false);

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);

            // When & Then
            assertThatThrownBy(
                            () ->
                                    documentMetadataService.updateDocument(
                                            publicId,
                                            projectId,
                                            memberId,
                                            documentId,
                                            request,
                                            null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("동일");
        }

        @Test
        @DisplayName("변경할 버전이 이미 존재 시 ConflictException")
        void updateDocument_DuplicateVersion_ThrowsConflictException() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            DocumentUpdateRequest request = new DocumentUpdateRequest(2, 0, 0, false);

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);
            given(documentMetadataManager.existsByGroupAndVersion(group, 2, 0, 0)).willReturn(true);

            // When & Then
            assertThatThrownBy(
                            () ->
                                    documentMetadataService.updateDocument(
                                            publicId,
                                            projectId,
                                            memberId,
                                            documentId,
                                            request,
                                            null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("이미 존재하는 버전");
        }

        @Test
        @DisplayName("파일을 전달했지만 해시가 동일하면 파일 변경 없이 처리")
        void updateDocument_FileWithSameHash_TreatedAsNoFileChange() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            MultipartFile file = mockFile("sameDoc.pdf");
            DocumentUpdateRequest request = new DocumentUpdateRequest(2, 0, 0, false);

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);
            given(documentFileStorage.computeHash(file)).willReturn("abc123hash");
            given(documentMetadataManager.existsByGroupAndVersion(group, 2, 0, 0))
                    .willReturn(false);

            // When
            documentMetadataService.updateDocument(
                    publicId, projectId, memberId, documentId, request, file);

            // Then
            assertThat(metadata.getMajorVersion()).isEqualTo(2);
            assertThat(metadata.getStoredKey()).isEqualTo("stored/key");
            then(documentFileStorage).should(never()).replace(any(), any());
            then(documentVectorEventPublisher)
                    .should(never())
                    .replaceEvent(any(), any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("동일 해시 파일로 교체 시 ConflictException")
        void updateDocument_DuplicateHash_ThrowsConflictException() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);
            MultipartFile file = mockFile("newDoc.pdf");
            DocumentUpdateRequest request = new DocumentUpdateRequest(1, 0, 0, false);

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);
            given(documentFileStorage.computeHash(file)).willReturn("newHash");
            given(documentMetadataManager.existsByProjectIdAndHash(projectId, "newHash"))
                    .willReturn(true);

            // When & Then
            assertThatThrownBy(
                            () ->
                                    documentMetadataService.updateDocument(
                                            publicId,
                                            projectId,
                                            memberId,
                                            documentId,
                                            request,
                                            file))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("동일한 내용");
        }
    }

    @Nested
    @DisplayName("문서 삭제")
    class DeleteDocument {

        @Test
        @DisplayName("정상 삭제 + 파일 삭제 예약 확인")
        void deleteDocument_ValidDocument_DeletesAndSchedulesFileCleanup() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);
            given(documentMetadataManager.countByGroup(group)).willReturn(2L);

            // When
            documentMetadataService.deleteDocument(projectId, documentId);

            // Then
            then(documentFileStorage).should().deleteOnCommit("stored/key");
            then(documentVectorEventPublisher).should().deleteEvent(projectId, metadata.getId());
            then(documentMetadataManager).should().delete(metadata);
            then(documentGroupManager).should(never()).delete(any());
        }

        @Test
        @DisplayName("마지막 문서 삭제 시 그룹도 삭제")
        void deleteDocument_LastDocumentInGroup_DeletesGroup() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);
            given(documentMetadataManager.countByGroup(group)).willReturn(1L);

            // When
            documentMetadataService.deleteDocument(projectId, documentId);

            // Then
            then(documentMetadataManager).should().delete(metadata);
            then(documentGroupManager).should().delete(group);
        }

        @Test
        @DisplayName("다른 문서가 남아있으면 그룹 유지")
        void deleteDocument_OtherDocumentsRemain_KeepsGroup() {
            // Given
            DocumentGroup group = createGroup();
            DocumentMetadata metadata = createMetadata(group);

            given(documentMetadataManager.getByIdAndProjectId(documentId, projectId))
                    .willReturn(metadata);
            given(documentMetadataManager.countByGroup(group)).willReturn(3L);

            // When
            documentMetadataService.deleteDocument(projectId, documentId);

            // Then
            then(documentMetadataManager).should().delete(metadata);
            then(documentGroupManager).should(never()).delete(any());
        }
    }
}

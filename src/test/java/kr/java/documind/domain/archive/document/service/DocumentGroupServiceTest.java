package kr.java.documind.domain.archive.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.document.infrastructure.DocumentGroupManager;
import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.archive.document.model.vo.DocumentGroupSummaryResult;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;
import kr.java.documind.global.entity.DomainSource;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentGroupService")
class DocumentGroupServiceTest {

    @Mock private DocumentGroupManager documentGroupManager;
    @Mock private DocumentMetadataManager documentMetadataManager;
    @InjectMocks private DocumentGroupService documentGroupService;

    private final UUID projectId = UUID.randomUUID();
    private final Long groupId = 1L;

    private DocumentGroup createGroup(String category, String groupName) {
        return DocumentGroup.create(projectId, category, groupName, "");
    }

    private DocumentMetadata createMetadata(DocumentGroup group) {
        DomainSource domainSource = DomainSource.create(SourceType.DOCUMENT);
        return DocumentMetadata.create(
                domainSource,
                group,
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
    @DisplayName("문서 그룹 목록 조회")
    class GetDocumentGroups {

        @Test
        @DisplayName("정상 조회 시 Page<DocumentGroupResponse> 반환")
        void getDocumentGroups_ValidProjectId_ReturnsPage() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            DocumentGroupSummaryResult summary =
                    new DocumentGroupSummaryResult(1L, "그룹A", "개발", "v1.0.0", 3);
            Page<DocumentGroupSummaryResult> summaryPage =
                    new PageImpl<>(List.of(summary), pageable, 1);

            given(documentGroupManager.findGroupSummaries(projectId, pageable))
                    .willReturn(summaryPage);

            // When
            var result = documentGroupService.getDocumentGroups(projectId, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).groupName()).isEqualTo("그룹A");
            assertThat(result.getContent().get(0).category()).isEqualTo("개발");
            assertThat(result.getContent().get(0).latestVersion()).isEqualTo("v1.0.0");
        }
    }

    @Nested
    @DisplayName("그룹별 문서 목록 조회")
    class GetDocumentsByGroup {

        @Test
        @DisplayName("정상 조회 시 버전 목록 반환")
        void getDocumentsByGroup_ValidGroup_ReturnsVersions() {
            // Given
            DocumentGroup group = createGroup("개발", "그룹A");
            DocumentMetadata metadata = createMetadata(group);

            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);
            given(documentMetadataManager.findVersionsByGroup(group)).willReturn(List.of(metadata));

            // When
            var result = documentGroupService.getDocumentsByGroup(projectId, groupId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).documentName()).isEqualTo("testDoc");
        }

        @Test
        @DisplayName("존재하지 않는 그룹 시 NotFoundException")
        void getDocumentsByGroup_GroupNotFound_ThrowsNotFoundException() {
            // Given
            given(documentGroupManager.getByIdAndProjectId(groupId, projectId))
                    .willThrow(new NotFoundException("프로젝트에서 문서 그룹을 찾을 수 없습니다."));

            // When & Then
            assertThatThrownBy(() -> documentGroupService.getDocumentsByGroup(projectId, groupId))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("그룹명 수정")
    class UpdateGroupName {

        @Test
        @DisplayName("정상 변경")
        void updateGroupName_ValidNewName_UpdatesSuccessfully() {
            // Given
            DocumentGroup group = createGroup("개발", "기존그룹");
            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);

            // When
            documentGroupService.updateGroupName(projectId, groupId, "새그룹");

            // Then
            assertThat(group.getGroupName()).isEqualTo("새그룹");
        }

        @Test
        @DisplayName("동일 이름으로 변경 시 ConflictException")
        void updateGroupName_SameName_ThrowsConflictException() {
            // Given
            DocumentGroup group = createGroup("개발", "기존그룹");
            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);

            // When & Then
            assertThatThrownBy(
                            () -> documentGroupService.updateGroupName(projectId, groupId, "기존그룹"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("동일");

            then(documentGroupManager)
                    .should(never())
                    .validateGroupNameUniqueness(any(), any(), any());
        }

        @Test
        @DisplayName("공백 포함 입력 시 trim 처리 후 변경")
        void updateGroupName_InputWithWhitespace_TrimsAndUpdates() {
            // Given
            DocumentGroup group = createGroup("개발", "기존그룹");
            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);

            // When
            documentGroupService.updateGroupName(projectId, groupId, "  새그룹  ");

            // Then
            assertThat(group.getGroupName()).isEqualTo("새그룹");
        }

        @Test
        @DisplayName("공백만 있는 입력이 trim 후 기존 이름과 동일하면 ConflictException")
        void updateGroupName_WhitespaceAroundSameName_ThrowsConflictException() {
            // Given
            DocumentGroup group = createGroup("개발", "기존그룹");
            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);

            // When & Then
            assertThatThrownBy(
                            () ->
                                    documentGroupService.updateGroupName(
                                            projectId, groupId, "  기존그룹  "))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("동일");
        }

        @Test
        @DisplayName("같은 카테고리 내 중복 이름 시 ConflictException")
        void updateGroupName_DuplicateNameInCategory_ThrowsConflictException() {
            // Given
            DocumentGroup group = createGroup("개발", "기존그룹");
            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);
            doThrow(new ConflictException("문서 그룹명(새그룹)이 카테고리(개발)에 이미 존재합니다."))
                    .when(documentGroupManager)
                    .validateGroupNameUniqueness(projectId, "개발", "새그룹");

            // When & Then
            assertThatThrownBy(
                            () -> documentGroupService.updateGroupName(projectId, groupId, "새그룹"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("이미 존재");
        }
    }

    @Nested
    @DisplayName("카테고리 수정")
    class UpdateCategory {

        @Test
        @DisplayName("정상 변경")
        void updateCategory_ValidNewCategory_UpdatesSuccessfully() {
            // Given
            DocumentGroup group = createGroup("개발", "그룹A");
            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);

            // When
            documentGroupService.updateCategory(projectId, groupId, "운영");

            // Then
            assertThat(group.getCategory()).isEqualTo("운영");
        }

        @Test
        @DisplayName("동일 카테고리로 변경 시 ConflictException")
        void updateCategory_SameCategory_ThrowsConflictException() {
            // Given
            DocumentGroup group = createGroup("개발", "그룹A");
            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);

            // When & Then
            assertThatThrownBy(() -> documentGroupService.updateCategory(projectId, groupId, "개발"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("동일");

            then(documentGroupManager)
                    .should(never())
                    .validateGroupNameUniqueness(any(), any(), any());
        }

        @Test
        @DisplayName("공백 포함 입력 시 trim 처리 후 변경")
        void updateCategory_InputWithWhitespace_TrimsAndUpdates() {
            // Given
            DocumentGroup group = createGroup("개발", "그룹A");
            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);

            // When
            documentGroupService.updateCategory(projectId, groupId, "  운영  ");

            // Then
            assertThat(group.getCategory()).isEqualTo("운영");
        }

        @Test
        @DisplayName("공백만 있는 입력이 trim 후 기존 카테고리와 동일하면 ConflictException")
        void updateCategory_WhitespaceAroundSameCategory_ThrowsConflictException() {
            // Given
            DocumentGroup group = createGroup("개발", "그룹A");
            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);

            // When & Then
            assertThatThrownBy(
                            () -> documentGroupService.updateCategory(projectId, groupId, "  개발  "))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("동일");
        }

        @Test
        @DisplayName("같은 카테고리 내 중복 그룹명 존재 시 ConflictException")
        void updateCategory_DuplicateGroupNameInNewCategory_ThrowsConflictException() {
            // Given
            DocumentGroup group = createGroup("개발", "그룹A");
            given(documentGroupManager.getByIdAndProjectId(groupId, projectId)).willReturn(group);
            doThrow(new ConflictException("문서 그룹명(그룹A)이 카테고리(운영)에 이미 존재합니다."))
                    .when(documentGroupManager)
                    .validateGroupNameUniqueness(projectId, "운영", "그룹A");

            // When & Then
            assertThatThrownBy(() -> documentGroupService.updateCategory(projectId, groupId, "운영"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("이미 존재");
        }
    }
}

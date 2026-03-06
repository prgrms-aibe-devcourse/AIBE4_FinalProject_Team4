package kr.java.documind.domain.archive.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.dto.request.CategoryUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.request.GroupNameUpdateRequest;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentGroupResponse;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentMetadataResponse;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.archive.document.model.repository.DocumentGroupRepository;
import kr.java.documind.domain.archive.document.model.repository.DocumentGroupSummary;
import kr.java.documind.domain.archive.document.model.repository.DocumentMetadataRepository;
import kr.java.documind.domain.member.model.entity.Project;
import kr.java.documind.global.entity.DomainSource;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DocumentGroupServiceTest {

    private DocumentGroupService documentGroupService;

    @Mock private DocumentGroupRepository documentGroupRepository;
    @Mock private DocumentMetadataRepository documentMetadataRepository;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final Project PROJECT = Project.create("test-project");
    private static final Long GROUP_ID = 1L;

    @BeforeEach
    void setUp() {
        documentGroupService =
                new DocumentGroupService(documentGroupRepository, documentMetadataRepository);
    }

    @Nested
    @DisplayName("getDocumentGroups")
    class GetDocumentGroups {

        @Test
        @DisplayName("프로젝트 ID로 그룹 목록을 조회한다")
        void returnsGroupsByProjectId() {
            Pageable pageable = PageRequest.of(0, 10);
            DocumentGroupSummary summary = mock(DocumentGroupSummary.class);
            given(summary.getGroupId()).willReturn(1L);
            given(summary.getGroupName()).willReturn("설계문서");
            given(summary.getCategory()).willReturn("기술");
            given(summary.getVersionOrdinal()).willReturn(1_000_000L);
            given(summary.getDocumentCount()).willReturn(3L);

            Page<DocumentGroupSummary> summaryPage = new PageImpl<>(List.of(summary), pageable, 1);
            given(documentGroupRepository.findGroupSummariesByProjectId(PROJECT_ID, pageable))
                    .willReturn(summaryPage);

            Page<DocumentGroupResponse> result =
                    documentGroupService.getDocumentGroups(PROJECT_ID, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).groupName()).isEqualTo("설계문서");
        }
    }

    @Nested
    @DisplayName("getDocumentVersions")
    class GetDocumentVersions {

        @Test
        @DisplayName("그룹 ID로 버전 목록을 조회한다")
        void returnsVersionsByGroupId() {
            DocumentGroup group = DocumentGroup.create(PROJECT, "기술", "설계문서", "");
            DocumentMetadata metadata =
                    DocumentMetadata.create(
                            DomainSource.create(SourceType.DOCUMENT),
                            group,
                            "doc",
                            "",
                            "pdf",
                            1,
                            0,
                            0,
                            "hash1",
                            1024L,
                            "key1",
                            false,
                            java.time.LocalDateTime.now());

            given(documentGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
            given(
                            documentMetadataRepository
                                    .findByDocumentGroupOrderByMajorVersionDescMinorVersionDescPatchVersionDesc(
                                            group))
                    .willReturn(List.of(metadata));

            List<DocumentMetadataResponse> result =
                    documentGroupService.getDocumentVersions(GROUP_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).version()).isEqualTo("v1.0.0");
        }

        @Test
        @DisplayName("존재하지 않는 그룹이면 NotFoundException을 던진다")
        void throwsNotFoundExceptionWhenGroupNotExists() {
            given(documentGroupRepository.findById(GROUP_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> documentGroupService.getDocumentVersions(GROUP_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(GROUP_ID));
        }
    }

    @Nested
    @DisplayName("updateGroupName")
    class UpdateGroupName {

        @Test
        @DisplayName("그룹명을 변경한다")
        void updatesGroupName() {
            DocumentGroup group = DocumentGroup.create(PROJECT, "기술", "설계문서", "");
            GroupNameUpdateRequest request = new GroupNameUpdateRequest("API문서");

            given(documentGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
            given(
                            documentGroupRepository.existsByProjectAndCategoryAndGroupName(
                                    PROJECT, "기술", "API문서"))
                    .willReturn(false);

            documentGroupService.updateGroupName(GROUP_ID, request);

            assertThat(group.getGroupName()).isEqualTo("API문서");
        }

        @Test
        @DisplayName("동일 카테고리에 중복 그룹명이면 ConflictException을 던진다")
        void throwsConflictExceptionWhenDuplicateName() {
            DocumentGroup group = DocumentGroup.create(PROJECT, "기술", "설계문서", "");
            GroupNameUpdateRequest request = new GroupNameUpdateRequest("API문서");

            given(documentGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
            given(
                            documentGroupRepository.existsByProjectAndCategoryAndGroupName(
                                    PROJECT, "기술", "API문서"))
                    .willReturn(true);

            assertThatThrownBy(() -> documentGroupService.updateGroupName(GROUP_ID, request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("이미 존재");
        }

        @Test
        @DisplayName("존재하지 않는 그룹이면 NotFoundException을 던진다")
        void throwsNotFoundExceptionWhenGroupNotExists() {
            GroupNameUpdateRequest request = new GroupNameUpdateRequest("API문서");

            given(documentGroupRepository.findById(GROUP_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> documentGroupService.updateGroupName(GROUP_ID, request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(GROUP_ID));
        }
    }

    @Nested
    @DisplayName("updateGroupCategory")
    class UpdateGroupCategory {

        @Test
        @DisplayName("카테고리를 변경한다")
        void updatesCategory() {
            DocumentGroup group = DocumentGroup.create(PROJECT, "기술", "설계문서", "");
            CategoryUpdateRequest request = new CategoryUpdateRequest("경영");

            given(documentGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
            given(
                            documentGroupRepository.existsByProjectAndCategoryAndGroupName(
                                    PROJECT, "경영", "설계문서"))
                    .willReturn(false);

            documentGroupService.updateGroupCategory(GROUP_ID, request);

            assertThat(group.getCategory()).isEqualTo("경영");
        }

        @Test
        @DisplayName("변경할 카테고리에 동일 그룹명 존재 시 ConflictException을 던진다")
        void throwsConflictExceptionWhenDuplicateNameInTargetCategory() {
            DocumentGroup group = DocumentGroup.create(PROJECT, "기술", "설계문서", "");
            CategoryUpdateRequest request = new CategoryUpdateRequest("경영");

            given(documentGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
            given(
                            documentGroupRepository.existsByProjectAndCategoryAndGroupName(
                                    PROJECT, "경영", "설계문서"))
                    .willReturn(true);

            assertThatThrownBy(() -> documentGroupService.updateGroupCategory(GROUP_ID, request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("이미 존재");
        }

        @Test
        @DisplayName("존재하지 않는 그룹이면 NotFoundException을 던진다")
        void throwsNotFoundExceptionWhenGroupNotExists() {
            CategoryUpdateRequest request = new CategoryUpdateRequest("경영");

            given(documentGroupRepository.findById(GROUP_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> documentGroupService.updateGroupCategory(GROUP_ID, request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(GROUP_ID));
        }
    }
}

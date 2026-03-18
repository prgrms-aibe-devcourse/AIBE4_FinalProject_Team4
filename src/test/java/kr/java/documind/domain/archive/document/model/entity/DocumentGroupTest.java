package kr.java.documind.domain.archive.document.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DocumentGroup")
class DocumentGroupTest {

    private final UUID projectId = UUID.randomUUID();

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("정적 팩토리로 생성 시 모든 필드가 설정된다")
        void create_ValidInput_SetsAllFields() {
            DocumentGroup group = DocumentGroup.create(projectId, "개발", "설계문서");

            assertThat(group.getProjectId()).isEqualTo(projectId);
            assertThat(group.getCategory()).isEqualTo("개발");
            assertThat(group.getGroupName()).isEqualTo("설계문서");
        }

        @Test
        @DisplayName("초성이 자동 설정된다")
        void create_SetsChoseong() {
            DocumentGroup group = DocumentGroup.create(projectId, "개발", "설계문서");

            assertThat(group.getChoseong()).isNotNull();
            assertThat(group.getChoseong()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("updateCategory")
    class UpdateCategory {

        @Test
        @DisplayName("카테고리가 변경된다")
        void updateCategory_ChangesCategory() {
            DocumentGroup group = DocumentGroup.create(projectId, "개발", "설계문서");

            group.updateCategory("운영");

            assertThat(group.getCategory()).isEqualTo("운영");
        }

        @Test
        @DisplayName("카테고리 변경 시 다른 필드는 유지된다")
        void updateCategory_OtherFieldsUnchanged() {
            DocumentGroup group = DocumentGroup.create(projectId, "개발", "설계문서");
            String originalGroupName = group.getGroupName();
            String originalChoseong = group.getChoseong();

            group.updateCategory("운영");

            assertThat(group.getGroupName()).isEqualTo(originalGroupName);
            assertThat(group.getChoseong()).isEqualTo(originalChoseong);
        }
    }

    @Nested
    @DisplayName("updateGroupName")
    class UpdateGroupName {

        @Test
        @DisplayName("그룹명이 변경된다")
        void updateGroupName_ChangesGroupName() {
            DocumentGroup group = DocumentGroup.create(projectId, "개발", "설계문서");

            group.updateGroupName("API문서");

            assertThat(group.getGroupName()).isEqualTo("API문서");
        }

        @Test
        @DisplayName("그룹명 변경 시 초성이 재설정된다")
        void updateGroupName_UpdatesChoseong() {
            DocumentGroup group = DocumentGroup.create(projectId, "개발", "설계문서");
            String originalChoseong = group.getChoseong();

            group.updateGroupName("API문서");

            assertThat(group.getChoseong()).isNotNull();
            assertThat(group.getChoseong()).isNotEqualTo(originalChoseong);
        }

        @Test
        @DisplayName("그룹명 변경 시 카테고리는 유지된다")
        void updateGroupName_CategoryUnchanged() {
            DocumentGroup group = DocumentGroup.create(projectId, "개발", "설계문서");

            group.updateGroupName("API문서");

            assertThat(group.getCategory()).isEqualTo("개발");
        }
    }
}

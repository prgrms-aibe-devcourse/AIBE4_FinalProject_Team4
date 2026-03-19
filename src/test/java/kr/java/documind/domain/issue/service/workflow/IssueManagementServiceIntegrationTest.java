package kr.java.documind.domain.issue.service.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.entity.IssueHistory;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssuePriority;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.enums.IssueType;
import kr.java.documind.domain.issue.model.repository.IssueHistoryRepository;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.domain.patchnote.event.IssueStatusChangedEvent;
import kr.java.documind.global.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import kr.java.documind.global.storage.FileStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * IssueManagementService 통합 테스트
 *
 * <p>이력 저장 정합성 및 이벤트 발행 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
@EnableAutoConfiguration(excludeName = {
    "org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration",
    "org.springframework.ai.autoconfigure.openai.OpenAiEmbeddingAutoConfiguration",
    "org.springframework.ai.autoconfigure.vertexai.gemini.VertexAiGeminiAutoConfiguration",
    "io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration",
    "io.awspring.cloud.autoconfigure.core.AwsAutoConfiguration"
})
@DisplayName("IssueManagementService 통합 테스트")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.flyway.enabled=false",
            "spring.cloud.aws.s3.bucket="
        })
class IssueManagementServiceIntegrationTest {

    @Autowired private IssueManagementService issueManagementService;

    @Autowired private IssueRepository issueRepository;

    @Autowired private IssueHistoryRepository issueHistoryRepository;

    @Autowired private ApplicationEvents applicationEvents;

    @MockBean private FileStore fileStore;
    @MockBean(name = "openAiEmbeddingModel") private EmbeddingModel embeddingModel;

    private UUID testProjectId;
    private UUID testModifierId;
    private Issue testIssue;

    @BeforeEach
    void setUp() {
        testProjectId = UUID.randomUUID();
        testModifierId = UUID.randomUUID();

        // 테스트용 이슈 생성 (TODO 상태)
        testIssue =
                Issue.builder()
                        .projectId(testProjectId)
                        .title("테스트 이슈")
                        .description("테스트 설명")
                        .fingerprint("test-fingerprint-" + System.currentTimeMillis())
                        .issueType(IssueType.BUG)
                        .status(IssueStatus.TODO)
                        .priority(IssuePriority.valueOf("P2"))
                        .severity(IssueSeverity.MEDIUM)
                        .severityScore(50)
                        .errorType(ErrorType.NULL_POINTER)
                        .stackKey("TestService.testMethod:42")
                        .occurrenceCount(10)
                        .firstOccurredAt(OffsetDateTime.now(java.time.ZoneOffset.UTC).minusDays(1))
                        .lastOccurredAt(OffsetDateTime.now(java.time.ZoneOffset.UTC))
                        .build();

        testIssue = issueRepository.save(testIssue);
    }

    @Nested
    @DisplayName("상태 변경 및 이력 저장 테스트")
    class StatusChangeAndHistoryTest {

        @Test
        @DisplayName("TODO → IN_PROGRESS 상태 변경 시 이력이 정상 저장됨")
        void todoToInProgressWithHistory() {
            // given
            Long issueId = testIssue.getId();
            IssueStatus newStatus = IssueStatus.IN_PROGRESS;

            // when
            issueManagementService.updateIssueStatus(
                    issueId, testProjectId, newStatus, null, testModifierId, false);

            // then
            // 1. 이슈 상태 변경 확인
            Issue updatedIssue = issueRepository.findById(issueId).orElseThrow();
            assertThat(updatedIssue.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);

            // 2. 이력 저장 확인
            List<IssueHistory> histories =
                    issueHistoryRepository.findByIssueIdOrderByCreatedAtDesc(issueId);
            assertThat(histories).hasSize(1);

            IssueHistory history = histories.get(0);
            assertThat(history.getIssueId()).isEqualTo(issueId);
            assertThat(history.getModifierId()).isEqualTo(testModifierId);
            assertThat(history.getFieldName()).isEqualTo("STATUS");
            assertThat(history.getBeforeValue()).isEqualTo("TODO");
            assertThat(history.getAfterValue()).isEqualTo("IN_PROGRESS");
            assertThat(history.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("IN_PROGRESS → RESOLVED 상태 변경 시 이력이 정상 저장됨")
        void inProgressToResolvedWithHistory() {
            // given
            testIssue.changeStatus(IssueStatus.IN_PROGRESS);
            issueRepository.save(testIssue);

            Long issueId = testIssue.getId();
            IssueStatus newStatus = IssueStatus.RESOLVED;

            // when
            issueManagementService.updateIssueStatus(
                    issueId, testProjectId, newStatus, null, testModifierId, false);

            // then
            // 1. 이슈 상태 변경 확인
            Issue updatedIssue = issueRepository.findById(issueId).orElseThrow();
            assertThat(updatedIssue.getStatus()).isEqualTo(IssueStatus.RESOLVED);
            assertThat(updatedIssue.getResolvedAt()).isNotNull();

            // 2. 이력 저장 확인
            List<IssueHistory> histories =
                    issueHistoryRepository.findByIssueIdOrderByCreatedAtDesc(issueId);
            assertThat(histories).hasSize(1);

            IssueHistory history = histories.get(0);
            assertThat(history.getBeforeValue()).isEqualTo("IN_PROGRESS");
            assertThat(history.getAfterValue()).isEqualTo("RESOLVED");
        }

        @Test
        @DisplayName("해결 방법 설명이 포함된 경우 resolutionNote가 저장됨")
        void resolvedWithResolutionNote() {
            // given
            testIssue.changeStatus(IssueStatus.IN_PROGRESS);
            issueRepository.save(testIssue);

            Long issueId = testIssue.getId();
            String resolutionNote = "null 체크 로직 추가하여 해결";

            // when
            issueManagementService.updateIssueStatus(
                    issueId,
                    testProjectId,
                    IssueStatus.RESOLVED,
                    resolutionNote,
                    testModifierId,
                    false);

            // then
            Issue updatedIssue = issueRepository.findById(issueId).orElseThrow();
            assertThat(updatedIssue.getResolutionNote()).isEqualTo(resolutionNote);
        }

        @Test
        @DisplayName("변경자 ID가 이력에 정확히 기록됨")
        void modifierIdRecordedCorrectly() {
            // given
            UUID specificModifierId = UUID.randomUUID();

            // when
            issueManagementService.updateIssueStatus(
                    testIssue.getId(),
                    testProjectId,
                    IssueStatus.IN_PROGRESS,
                    null,
                    specificModifierId,
                    false);

            // then
            List<IssueHistory> histories =
                    issueHistoryRepository.findByIssueIdOrderByCreatedAtDesc(testIssue.getId());
            assertThat(histories.get(0).getModifierId()).isEqualTo(specificModifierId);
        }
    }

    @Nested
    @DisplayName("상태 전환 규칙 위반 차단 테스트")
    class StatusTransitionValidationTest {

        @Test
        @DisplayName("TODO → RESOLVED 직접 전환 시 예외 발생")
        void todoToResolvedDirectlyBlocked() {
            // given
            Long issueId = testIssue.getId();

            // when & then
            assertThatThrownBy(
                            () ->
                                    issueManagementService.updateIssueStatus(
                                            issueId,
                                            testProjectId,
                                            IssueStatus.RESOLVED,
                                            null,
                                            testModifierId,
                                            false))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("허용되지 않는 상태 전환입니다");

            // 이슈 상태가 변경되지 않았는지 확인
            Issue unchangedIssue = issueRepository.findById(issueId).orElseThrow();
            assertThat(unchangedIssue.getStatus()).isEqualTo(IssueStatus.TODO);

            // 이력이 저장되지 않았는지 확인
            List<IssueHistory> histories =
                    issueHistoryRepository.findByIssueIdOrderByCreatedAtDesc(issueId);
            assertThat(histories).isEmpty();
        }

        @Test
        @DisplayName("RESOLVED → TODO 역행 시 예외 발생")
        void resolvedToTodoBlocked() {
            // given
            testIssue.changeStatus(IssueStatus.IN_PROGRESS);
            testIssue.changeStatus(IssueStatus.RESOLVED);
            issueRepository.save(testIssue);

            Long issueId = testIssue.getId();

            // when & then
            assertThatThrownBy(
                            () ->
                                    issueManagementService.updateIssueStatus(
                                            issueId,
                                            testProjectId,
                                            IssueStatus.TODO,
                                            null,
                                            testModifierId,
                                            false))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("허용되지 않는 상태 전환입니다");

            // 이슈 상태가 변경되지 않았는지 확인
            Issue unchangedIssue = issueRepository.findById(issueId).orElseThrow();
            assertThat(unchangedIssue.getStatus()).isEqualTo(IssueStatus.RESOLVED);
        }

        @Test
        @DisplayName("동일 상태로 변경 시도 시 예외 발생")
        void sameStatusChangeBlocked() {
            // given
            Long issueId = testIssue.getId();

            // when & then
            assertThatThrownBy(
                            () ->
                                    issueManagementService.updateIssueStatus(
                                            issueId,
                                            testProjectId,
                                            IssueStatus.TODO,
                                            null,
                                            testModifierId,
                                            false))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("현재 상태와 동일한 상태로는 변경할 수 없습니다");
        }
    }

    @Nested
    @DisplayName("이벤트 발행 테스트")
    class EventPublishingTest {

        @Test
        @DisplayName("RESOLVED 상태로 변경 시 IssueStatusChangedEvent 발행됨")
        void issueStatusChangedEventPublished() {
            // given
            testIssue.changeStatus(IssueStatus.IN_PROGRESS);
            issueRepository.save(testIssue);

            Long issueId = testIssue.getId();

            // when
            issueManagementService.updateIssueStatus(
                    issueId, testProjectId, IssueStatus.RESOLVED, null, testModifierId, true);

            // then
            long eventCount =
                    applicationEvents.stream(IssueStatusChangedEvent.class)
                            .filter(event -> event.issueId().equals(issueId))
                            .count();

            assertThat(eventCount).isEqualTo(1);

            IssueStatusChangedEvent publishedEvent =
                    applicationEvents.stream(IssueStatusChangedEvent.class)
                            .filter(event -> event.issueId().equals(issueId))
                            .findFirst()
                            .orElseThrow();

            assertThat(publishedEvent.issueId()).isEqualTo(issueId);
            assertThat(publishedEvent.projectId()).isEqualTo(testProjectId);
            assertThat(publishedEvent.newStatus()).isEqualTo(IssueStatus.RESOLVED);
            assertThat(publishedEvent.actorId())
                    .isEqualTo(testModifierId); // modifierId() -> actorId()
        }

        @Test
        @DisplayName("패치노트 미포함 시에도 이벤트는 발행되지만 excludeFromPatchNote가 true임")
        void eventPublishedWithExcludeFlagWhenNotIncludedInPatchNote() {
            // given
            testIssue.changeStatus(IssueStatus.IN_PROGRESS);
            issueRepository.save(testIssue);

            Long issueId = testIssue.getId();

            // when
            issueManagementService.updateIssueStatus(
                    issueId,
                    testProjectId,
                    IssueStatus.RESOLVED,
                    null,
                    testModifierId,
                    false); // false

            // then
            IssueStatusChangedEvent publishedEvent =
                    applicationEvents.stream(IssueStatusChangedEvent.class)
                            .filter(event -> event.issueId().equals(issueId))
                            .findFirst()
                            .orElseThrow();

            assertThat(publishedEvent.excludeFromPatchNote()).isTrue();
        }

        @Test
        @DisplayName("다른 상태로 변경 시에도 IssueStatusChangedEvent 발행됨")
        void eventPublishedForNonResolvedStatus() {
            // given
            Long issueId = testIssue.getId();

            // when
            issueManagementService.updateIssueStatus(
                    issueId, testProjectId, IssueStatus.IN_PROGRESS, null, testModifierId, true);

            // then
            long eventCount =
                    applicationEvents.stream(IssueStatusChangedEvent.class)
                            .filter(event -> event.issueId().equals(issueId))
                            .count();

            assertThat(eventCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("담당자 변경 테스트")
    class AssigneeChangeTest {

        @Test
        @DisplayName("담당자 변경 시 이력이 정상 저장됨")
        void assigneeChangeWithHistory() {
            // given
            Long issueId = testIssue.getId();
            UUID newAssigneeId = UUID.randomUUID();

            // when
            issueManagementService.assignIssue(
                    issueId, testProjectId, newAssigneeId, testModifierId);

            // then
            // 1. 담당자 변경 확인
            Issue updatedIssue = issueRepository.findById(issueId).orElseThrow();
            assertThat(updatedIssue.getAssigneeId()).isEqualTo(newAssigneeId);

            // 2. 이력 저장 확인
            List<IssueHistory> histories =
                    issueHistoryRepository.findByIssueIdAndFieldNameOrderByCreatedAtDesc(
                            issueId, "ASSIGNEE");
            assertThat(histories).hasSize(1);

            IssueHistory history = histories.get(0);
            assertThat(history.getFieldName()).isEqualTo("ASSIGNEE");
            assertThat(history.getBeforeValue()).isNull(); // 최초 할당
            assertThat(history.getAfterValue()).isEqualTo(newAssigneeId.toString());
        }
    }
}

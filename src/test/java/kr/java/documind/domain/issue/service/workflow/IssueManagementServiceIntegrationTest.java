package kr.java.documind.domain.issue.service.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.event.IssueResolvedEvent;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.entity.IssueHistory;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.enums.IssueType;
import kr.java.documind.domain.issue.model.repository.IssueHistoryRepository;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.global.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
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
@DisplayName("IssueManagementService 통합 테스트")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:testdb",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.flyway.enabled=false"
        })
class IssueManagementServiceIntegrationTest {

    @Autowired private IssueManagementService issueManagementService;

    @Autowired private IssueRepository issueRepository;

    @Autowired private IssueHistoryRepository issueHistoryRepository;

    @Autowired private ApplicationEvents applicationEvents;

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
                        .priority("P2")
                        .severity(IssueSeverity.MEDIUM)
                        .severityScore(50)
                        .errorType(ErrorType.NULL_POINTER)
                        .stackKey("TestService.testMethod:42")
                        .occurrenceCount(10)
                        .firstOccurredAt(OffsetDateTime.now().minusDays(1))
                        .lastOccurredAt(OffsetDateTime.now())
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
                    issueId, newStatus, null, testModifierId, false);

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
            assertThat(history.getBeforeValue()).isEqualTo("대기중");
            assertThat(history.getAfterValue()).isEqualTo("처리중");
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
                    issueId, newStatus, null, testModifierId, false);

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
            assertThat(history.getBeforeValue()).isEqualTo("처리중");
            assertThat(history.getAfterValue()).isEqualTo("해결됨");
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
                    issueId, IssueStatus.RESOLVED, resolutionNote, testModifierId, false);

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
                    testIssue.getId(), IssueStatus.IN_PROGRESS, null, specificModifierId, false);

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
                                            issueId, IssueStatus.TODO, null, testModifierId, false))
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
                                            issueId, IssueStatus.TODO, null, testModifierId, false))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("현재 상태와 동일한 상태로는 변경할 수 없습니다");
        }
    }

    @Nested
    @DisplayName("이벤트 발행 테스트")
    class EventPublishingTest {

        @Test
        @DisplayName("RESOLVED 상태로 변경 시 IssueResolvedEvent 발행됨")
        void issueResolvedEventPublished() {
            // given
            testIssue.changeStatus(IssueStatus.IN_PROGRESS);
            issueRepository.save(testIssue);

            Long issueId = testIssue.getId();

            // when
            issueManagementService.updateIssueStatus(
                    issueId, IssueStatus.RESOLVED, null, testModifierId, true);

            // then
            long eventCount =
                    applicationEvents.stream(IssueResolvedEvent.class)
                            .filter(event -> event.issueId().equals(issueId))
                            .count();

            assertThat(eventCount).isEqualTo(1);

            IssueResolvedEvent publishedEvent =
                    applicationEvents.stream(IssueResolvedEvent.class)
                            .filter(event -> event.issueId().equals(issueId))
                            .findFirst()
                            .orElseThrow();

            assertThat(publishedEvent.issueId()).isEqualTo(issueId);
            assertThat(publishedEvent.projectId()).isEqualTo(testProjectId);
            assertThat(publishedEvent.title()).isEqualTo(testIssue.getTitle());
            assertThat(publishedEvent.resolvedBy()).isEqualTo(testModifierId);
        }

        @Test
        @DisplayName("패치노트 미포함 시 이벤트 발행되지 않음")
        void eventNotPublishedWhenNotIncludedInPatchNote() {
            // given
            testIssue.changeStatus(IssueStatus.IN_PROGRESS);
            issueRepository.save(testIssue);

            Long issueId = testIssue.getId();

            // when
            issueManagementService.updateIssueStatus(
                    issueId, IssueStatus.RESOLVED, null, testModifierId, false); // false

            // then
            long eventCount =
                    applicationEvents.stream(IssueResolvedEvent.class)
                            .filter(event -> event.issueId().equals(issueId))
                            .count();

            assertThat(eventCount).isZero();
        }

        @Test
        @DisplayName("다른 상태로 변경 시 IssueResolvedEvent 발행되지 않음")
        void eventNotPublishedForNonResolvedStatus() {
            // given
            Long issueId = testIssue.getId();

            // when
            issueManagementService.updateIssueStatus(
                    issueId, IssueStatus.IN_PROGRESS, null, testModifierId, true);

            // then
            long eventCount =
                    applicationEvents.stream(IssueResolvedEvent.class)
                            .filter(event -> event.issueId().equals(issueId))
                            .count();

            assertThat(eventCount).isZero();
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
            issueManagementService.assignIssue(issueId, newAssigneeId, testModifierId);

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

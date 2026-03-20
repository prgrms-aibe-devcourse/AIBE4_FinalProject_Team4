package kr.java.documind.domain.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.auth.model.enums.OAuthProvider;
import kr.java.documind.domain.auth.model.enums.ProjectRole;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import kr.java.documind.domain.issue.event.IssueNotificationEvent;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.entity.IssueAlertRule;
import kr.java.documind.domain.issue.model.enums.IssueAlertRuleKey;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.repository.IssueAlertRuleRepository;
import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.entity.ProjectMember;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.repository.ProjectMemberRepository;
import kr.java.documind.domain.notification.model.enums.NotificationEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IssueNotificationServiceTest {

    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private IssueAlertRuleRepository alertRuleRepository;

    @Mock private ProjectMemberRepository projectMemberRepository;

    @Mock private ProjectRepository projectRepository;

    @InjectMocks private IssueNotificationService notificationService;

    private UUID projectId;
    private UUID member1Id;
    private UUID member2Id;
    private Project project;
    private Issue issue;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        member1Id = UUID.randomUUID();
        member2Id = UUID.randomUUID();

        Company company = Company.create("Test Company");
        project = Project.create("test-project", company, "Test Project", null);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        issue =
                Issue.builder()
                        .projectId(projectId)
                        .title("NullPointerException in PaymentService")
                        .fingerprint("test-fingerprint")
                        .severity(IssueSeverity.CRITICAL)
                        .status(IssueStatus.TODO)
                        .occurrenceCount(1)
                        .firstOccurredAt(now)
                        .lastOccurredAt(now)
                        .build();
        ReflectionTestUtils.setField(issue, "id", 1L);
    }

    @Test
    @DisplayName("신규 이슈 생성 시 CRITICAL 심각도 알림을 켠 멤버들에게 알림 발송")
    void notifyNewIssue_Critical_Success() {
        // Given
        Company company = Company.create("Test Company");
        Member member1 = createTestMember("user1@test.com", "User1", "user1");
        Member member2 = createTestMember("user2@test.com", "User2", "user2");
        ReflectionTestUtils.setField(member1, "id", member1Id);
        ReflectionTestUtils.setField(member2, "id", member2Id);

        ProjectMember pm1 = ProjectMember.create(project, member1, ProjectRole.MEMBER);
        ProjectMember pm2 = ProjectMember.create(project, member2, ProjectRole.MEMBER);

        IssueAlertRule rule1 = IssueAlertRule.createDefault(projectId, member1Id);
        IssueAlertRule rule2 = IssueAlertRule.createDefault(projectId, member2Id);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectAndStatusFetchMember(
                        project, AccountStatus.ACTIVE))
                .thenReturn(List.of(pm1, pm2));
        when(alertRuleRepository.findByProjectIdAndMemberId(projectId, member1Id))
                .thenReturn(Optional.of(rule1));
        when(alertRuleRepository.findByProjectIdAndMemberId(projectId, member2Id))
                .thenReturn(Optional.of(rule2));

        // When
        notificationService.notifyNewIssue(issue);

        // Then
        ArgumentCaptor<IssueNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(IssueNotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        IssueNotificationEvent event = eventCaptor.getValue();
        assertThat(event.projectId()).isEqualTo(projectId);
        assertThat(event.receiverIds()).containsAll(List.of(member1Id, member2Id));
        assertThat(event.receiverIds()).hasSize(2);
        assertThat(event.eventType()).isEqualTo(NotificationEventType.ISSUE_CREATED);
        assertThat(event.title()).contains("신규 이슈");
        assertThat(event.message()).contains("CRITICAL");
        assertThat(event.isToast()).isTrue();
    }

    @Test
    @DisplayName("신규 이슈 생성 시 CRITICAL 알림을 끈 멤버는 제외")
    void notifyNewIssue_CriticalDisabled_Excluded() {
        // Given
        Company company = Company.create("Test Company");
        Member member1 = createTestMember("user1@test.com", "User1", "user1");
        Member member2 = createTestMember("user2@test.com", "User2", "user2");
        ReflectionTestUtils.setField(member1, "id", member1Id);
        ReflectionTestUtils.setField(member2, "id", member2Id);

        ProjectMember pm1 = ProjectMember.create(project, member1, ProjectRole.MEMBER);
        ProjectMember pm2 = ProjectMember.create(project, member2, ProjectRole.MEMBER);

        IssueAlertRule rule1 = IssueAlertRule.createDefault(projectId, member1Id);
        rule1.update(IssueAlertRuleKey.SEVERITY_CRITICAL, false); // CRITICAL 알림 끔

        IssueAlertRule rule2 = IssueAlertRule.createDefault(projectId, member2Id);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectAndStatusFetchMember(
                        project, AccountStatus.ACTIVE))
                .thenReturn(List.of(pm1, pm2));
        when(alertRuleRepository.findByProjectIdAndMemberId(projectId, member1Id))
                .thenReturn(Optional.of(rule1));
        when(alertRuleRepository.findByProjectIdAndMemberId(projectId, member2Id))
                .thenReturn(Optional.of(rule2));

        // When
        notificationService.notifyNewIssue(issue);

        // Then
        ArgumentCaptor<IssueNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(IssueNotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        IssueNotificationEvent event = eventCaptor.getValue();
        assertThat(event.receiverIds()).hasSize(1);
        assertThat(event.receiverIds()).contains(member2Id);
        assertThat(event.receiverIds()).doesNotContain(member1Id);
    }

    @Test
    @DisplayName("모든 멤버가 CRITICAL 알림을 끈 경우 이벤트 발행 안 함")
    void notifyNewIssue_AllDisabled_NoEvent() {
        // Given
        Company company = Company.create("Test Company");
        Member member1 = createTestMember("user1@test.com", "User1", "user1");
        Member member2 = createTestMember("user2@test.com", "User2", "user2");
        ReflectionTestUtils.setField(member1, "id", member1Id);
        ReflectionTestUtils.setField(member2, "id", member2Id);

        ProjectMember pm1 = ProjectMember.create(project, member1, ProjectRole.MEMBER);
        ProjectMember pm2 = ProjectMember.create(project, member2, ProjectRole.MEMBER);

        IssueAlertRule rule1 = IssueAlertRule.createDefault(projectId, member1Id);
        rule1.update(IssueAlertRuleKey.SEVERITY_CRITICAL, false);

        IssueAlertRule rule2 = IssueAlertRule.createDefault(projectId, member2Id);
        rule2.update(IssueAlertRuleKey.SEVERITY_CRITICAL, false);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectAndStatusFetchMember(
                        project, AccountStatus.ACTIVE))
                .thenReturn(List.of(pm1, pm2));
        when(alertRuleRepository.findByProjectIdAndMemberId(projectId, member1Id))
                .thenReturn(Optional.of(rule1));
        when(alertRuleRepository.findByProjectIdAndMemberId(projectId, member2Id))
                .thenReturn(Optional.of(rule2));

        // When
        notificationService.notifyNewIssue(issue);

        // Then
        verify(eventPublisher, never()).publishEvent(any(IssueNotificationEvent.class));
    }

    @Test
    @DisplayName("담당자 배정 시 해당 담당자에게 알림 발송")
    void notifyAssigneeChange_Success() {
        // Given
        UUID assigneeId = UUID.randomUUID();
        issue.assignTo(assigneeId);

        IssueAlertRule rule = IssueAlertRule.createDefault(projectId, assigneeId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(alertRuleRepository.findByProjectIdAndMemberId(projectId, assigneeId))
                .thenReturn(Optional.of(rule));

        // When
        notificationService.notifyAssigneeChange(issue, assigneeId);

        // Then
        ArgumentCaptor<IssueNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(IssueNotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        IssueNotificationEvent event = eventCaptor.getValue();
        assertThat(event.receiverIds()).hasSize(1);
        assertThat(event.receiverIds()).contains(assigneeId);
        assertThat(event.eventType()).isEqualTo(NotificationEventType.ISSUE_ASSIGNED);
        assertThat(event.title()).contains("담당자 배정");
    }

    @Test
    @DisplayName("담당자가 배정 알림을 끈 경우 이벤트 발행 안 함")
    void notifyAssigneeChange_Disabled_NoEvent() {
        // Given
        UUID assigneeId = UUID.randomUUID();
        issue.assignTo(assigneeId);

        IssueAlertRule rule = IssueAlertRule.createDefault(projectId, assigneeId);
        rule.update(IssueAlertRuleKey.ISSUE_ASSIGNED, false);

        when(alertRuleRepository.findByProjectIdAndMemberId(projectId, assigneeId))
                .thenReturn(Optional.of(rule));

        // When
        notificationService.notifyAssigneeChange(issue, assigneeId);

        // Then
        verify(eventPublisher, never()).publishEvent(any(IssueNotificationEvent.class));
    }

    @Test
    @DisplayName("상태 변경 시 담당자에게 알림 발송")
    void notifyStatusChange_Success() {
        // Given
        UUID assigneeId = UUID.randomUUID();
        issue.assignTo(assigneeId);
        issue.changeStatus(IssueStatus.IN_PROGRESS);

        IssueAlertRule rule = IssueAlertRule.createDefault(projectId, assigneeId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(alertRuleRepository.findByProjectIdAndMemberId(projectId, assigneeId))
                .thenReturn(Optional.of(rule));

        // When
        notificationService.notifyStatusChange(issue, IssueStatus.TODO, IssueStatus.IN_PROGRESS);

        // Then
        ArgumentCaptor<IssueNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(IssueNotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        IssueNotificationEvent event = eventCaptor.getValue();
        assertThat(event.receiverIds()).contains(assigneeId);
        assertThat(event.eventType()).isEqualTo(NotificationEventType.ISSUE_STATUS_CHANGED);
        assertThat(event.message()).contains("TODO → IN_PROGRESS");
    }

    @Test
    @DisplayName("댓글/멘션 시 멘션된 사용자와 담당자에게 알림 발송")
    void notifyComment_Success() {
        // Given
        UUID authorId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID mentionedId = UUID.randomUUID();

        issue.assignTo(assigneeId);

        IssueAlertRule assigneeRule = IssueAlertRule.createDefault(projectId, assigneeId);
        IssueAlertRule mentionedRule = IssueAlertRule.createDefault(projectId, mentionedId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(alertRuleRepository.findByProjectIdAndMemberId(projectId, assigneeId))
                .thenReturn(Optional.of(assigneeRule));
        when(alertRuleRepository.findByProjectIdAndMemberId(projectId, mentionedId))
                .thenReturn(Optional.of(mentionedRule));

        // When
        notificationService.notifyComment(issue, authorId, List.of(mentionedId));

        // Then
        ArgumentCaptor<IssueNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(IssueNotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        IssueNotificationEvent event = eventCaptor.getValue();
        assertThat(event.receiverIds()).containsAll(List.of(assigneeId, mentionedId));
        assertThat(event.eventType()).isEqualTo(NotificationEventType.ISSUE_MENTIONED);
    }

    @Test
    @DisplayName("댓글 작성자가 담당자인 경우 중복 제거")
    void notifyComment_AuthorIsAssignee_NoDuplicate() {
        // Given
        UUID authorId = UUID.randomUUID();
        UUID mentionedId = UUID.randomUUID();

        issue.assignTo(authorId); // 작성자가 담당자

        IssueAlertRule authorRule = IssueAlertRule.createDefault(projectId, authorId);
        IssueAlertRule mentionedRule = IssueAlertRule.createDefault(projectId, mentionedId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(alertRuleRepository.findByProjectIdAndMemberId(eq(projectId), any()))
                .thenAnswer(
                        invocation -> {
                            UUID memberId = invocation.getArgument(1);
                            if (memberId.equals(authorId)) return Optional.of(authorRule);
                            if (memberId.equals(mentionedId)) return Optional.of(mentionedRule);
                            return Optional.empty();
                        });

        // When
        notificationService.notifyComment(issue, authorId, List.of(mentionedId));

        // Then
        ArgumentCaptor<IssueNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(IssueNotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        IssueNotificationEvent event = eventCaptor.getValue();
        assertThat(event.receiverIds()).hasSize(1);
        assertThat(event.receiverIds()).contains(mentionedId);
        assertThat(event.receiverIds()).doesNotContain(authorId);
    }

    private Member createTestMember(String email, String name, String nickname) {
        return Member.createByOAuth(
                email, name, nickname, OAuthProvider.GITHUB, "test-provider-id", GlobalRole.EMPLOYEE);
    }
}

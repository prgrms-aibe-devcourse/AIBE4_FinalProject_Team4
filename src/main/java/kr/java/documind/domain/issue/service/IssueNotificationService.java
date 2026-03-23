package kr.java.documind.domain.issue.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import kr.java.documind.domain.issue.event.IssueNotificationEvent;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.entity.IssueAlertRule;
import kr.java.documind.domain.issue.model.enums.IssueAlertRuleKey;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.repository.IssueAlertRuleRepository;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.repository.ProjectMemberRepository;
import kr.java.documind.domain.notification.model.enums.NotificationEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이슈 알림 서비스
 *
 * <p>이슈 관련 이벤트 발생 시 알림 전송을 담당
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IssueNotificationService {

    private final ApplicationEventPublisher eventPublisher;
    private final IssueAlertRuleRepository alertRuleRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;

    /**
     * 신규 이슈 생성 알림
     *
     * <p>프로젝트 멤버 중 해당 심각도 알림을 켠 사람에게만 발송
     *
     * @param issue 생성된 이슈
     */
    public void notifyNewIssue(Issue issue) {
        // severity가 null인 경우 알림 생략
        if (issue.getSeverity() == null) {
            log.warn(
                    "[IssueNotification] Severity is null, skipping notification. issueId={}",
                    issue.getId());
            return;
        }

        IssueAlertRuleKey ruleKey = mapSeverityToRuleKey(issue.getSeverity());
        ReceiverInfo info = getReceiversForRule(issue.getProjectId(), ruleKey);

        if (info.receivers().isEmpty()) {
            log.debug(
                    "[IssueNotification] No receivers for new issue notification. issueId={}, severity={}",
                    issue.getId(),
                    issue.getSeverity());
            return;
        }

        IssueNotificationEvent event =
                new IssueNotificationEvent(
                        issue.getProjectId(),
                        info.receivers(),
                        issue.getId(),
                        NotificationEventType.ISSUE_CREATED,
                        "[신규 이슈] " + issue.getTitle(),
                        String.format("%s 심각도 이슈가 생성되었습니다.", issue.getSeverity().getValue()),
                        buildIssueUrl(info.publicId(), issue.getId()),
                        true, // toast 알림
                        issue.getSeverity());

        eventPublisher.publishEvent(event);
        log.info(
                "[IssueNotification] New issue notification sent. issueId={}, receiverCount={}",
                issue.getId(),
                info.receivers().size());
    }

    /**
     * 담당자 변경 알림
     *
     * @param issue 이슈
     * @param newAssigneeId 새 담당자 ID
     */
    public void notifyAssigneeChange(Issue issue, UUID newAssigneeId) {
        // 알림 규칙 확인
        if (!isRuleEnabled(issue.getProjectId(), newAssigneeId, IssueAlertRuleKey.ISSUE_ASSIGNED)) {
            log.debug(
                    "[IssueNotification] Assignee notification disabled. issueId={}, assigneeId={}",
                    issue.getId(),
                    newAssigneeId);
            return;
        }

        String publicId = getProjectPublicId(issue.getProjectId());
        IssueNotificationEvent event =
                new IssueNotificationEvent(
                        issue.getProjectId(),
                        List.of(newAssigneeId),
                        issue.getId(),
                        NotificationEventType.ISSUE_ASSIGNED,
                        "[담당자 배정] " + issue.getTitle(),
                        "이슈가 회원님에게 배정되었습니다.",
                        buildIssueUrl(publicId, issue.getId()),
                        true,
                        issue.getSeverity());

        eventPublisher.publishEvent(event);
        log.info(
                "[IssueNotification] Assignee change notification sent. issueId={}, assigneeId={}",
                issue.getId(),
                newAssigneeId);
    }

    /**
     * 상태 변경 알림
     *
     * <p>프로젝트 멤버 중 상태 변경 알림을 켠 사람에게만 발송
     *
     * @param issue 이슈
     * @param beforeStatus 이전 상태
     * @param newStatus 새 상태
     */
    public void notifyStatusChange(Issue issue, IssueStatus beforeStatus, IssueStatus newStatus) {
        ReceiverInfo info =
                getReceiversForRule(issue.getProjectId(), IssueAlertRuleKey.ISSUE_STATUS_CHANGED);

        if (info.receivers().isEmpty()) {
            log.debug(
                    "[IssueNotification] No receivers for status change notification. issueId={}",
                    issue.getId());
            return;
        }

        IssueNotificationEvent event =
                new IssueNotificationEvent(
                        issue.getProjectId(),
                        info.receivers(),
                        issue.getId(),
                        NotificationEventType.ISSUE_STATUS_CHANGED,
                        "[상태 변경] " + issue.getTitle(),
                        String.format("%s → %s", beforeStatus.getValue(), newStatus.getValue()),
                        buildIssueUrl(info.publicId(), issue.getId()),
                        true,
                        issue.getSeverity());

        eventPublisher.publishEvent(event);
        log.info(
                "[IssueNotification] Status change notification sent. issueId={}, receiverCount={}",
                issue.getId(),
                info.receivers().size());
    }

    /**
     * 댓글/멘션 알림
     *
     * @param issue 이슈
     * @param authorId 댓글 작성자 ID
     * @param mentionedIds 멘션된 사용자 ID 목록
     */
    public void notifyComment(Issue issue, UUID authorId, List<UUID> mentionedIds) {
        List<UUID> receivers = new ArrayList<>();

        // 1. 멘션된 사람들 추가
        receivers.addAll(mentionedIds);

        // 2. 담당자 추가 (댓글 작성자가 아닌 경우)
        if (issue.getAssigneeId() != null && !issue.getAssigneeId().equals(authorId)) {
            receivers.add(issue.getAssigneeId());
        }

        // 중복 제거 + 규칙 필터링
        receivers =
                receivers.stream()
                        .distinct()
                        .filter(
                                r ->
                                        isRuleEnabled(
                                                issue.getProjectId(),
                                                r,
                                                IssueAlertRuleKey.ISSUE_MENTIONED))
                        .toList();

        if (receivers.isEmpty()) {
            log.debug(
                    "[IssueNotification] No receivers for comment notification. issueId={}",
                    issue.getId());
            return;
        }

        String publicId = getProjectPublicId(issue.getProjectId());
        IssueNotificationEvent event =
                new IssueNotificationEvent(
                        issue.getProjectId(),
                        receivers,
                        issue.getId(),
                        NotificationEventType.ISSUE_MENTIONED,
                        "[댓글] " + issue.getTitle(),
                        "새 댓글이 달렸습니다.",
                        buildIssueUrl(publicId, issue.getId()),
                        true,
                        issue.getSeverity());

        eventPublisher.publishEvent(event);
        log.info(
                "[IssueNotification] Comment notification sent. issueId={}, receiverCount={}",
                issue.getId(),
                receivers.size());
    }

    /**
     * 특정 알림 규칙에 해당하는 수신자 목록 조회
     *
     * @param projectId 프로젝트 ID
     * @param ruleKey 알림 규칙 키
     * @return 수신자 정보 (멤버 ID 목록 + publicId)
     */
    private ReceiverInfo getReceiversForRule(UUID projectId, IssueAlertRuleKey ruleKey) {
        // 프로젝트 조회 (1회만)
        Project project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Project not found: " + projectId));

        // 프로젝트 전체 활성 멤버 조회
        List<UUID> allMembers =
                projectMemberRepository
                        .findByProjectAndStatusFetchMember(project, AccountStatus.ACTIVE)
                        .stream()
                        .map(pm -> pm.getMember().getId())
                        .toList();

        // 모든 알림 규칙을 한 번에 조회 (N+1 방지)
        List<IssueAlertRule> rules =
                alertRuleRepository.findByProjectIdAndMemberIdIn(projectId, allMembers);
        Map<UUID, IssueAlertRule> ruleMap =
                rules.stream().collect(Collectors.toMap(IssueAlertRule::getMemberId, r -> r));

        // 알림 규칙 필터링
        List<UUID> receivers =
                allMembers.stream()
                        .filter(
                                memberId -> {
                                    IssueAlertRule rule = ruleMap.get(memberId);
                                    return rule == null
                                            || rule.isEnabled(ruleKey); // 규칙 미설정 시 기본값 true
                                })
                        .toList();

        return new ReceiverInfo(receivers, project.getPublicId());
    }

    /** 수신자 정보 (멤버 ID 목록 + publicId) */
    private record ReceiverInfo(List<UUID> receivers, String publicId) {}

    /**
     * 알림 규칙 활성화 여부 확인
     *
     * @param projectId 프로젝트 ID
     * @param memberId 멤버 ID
     * @param ruleKey 알림 규칙 키
     * @return 활성화 여부 (규칙 미설정 시 기본값 true)
     */
    private boolean isRuleEnabled(UUID projectId, UUID memberId, IssueAlertRuleKey ruleKey) {
        return alertRuleRepository
                .findByProjectIdAndMemberId(projectId, memberId)
                .map(rule -> rule.isEnabled(ruleKey))
                .orElse(true); // 규칙 미설정 시 기본값 true
    }

    /**
     * 심각도를 알림 규칙 키로 매핑
     *
     * @param severity 이슈 심각도 (null이 아니어야 함)
     * @return 알림 규칙 키
     * @throws IllegalArgumentException severity가 null인 경우
     */
    private IssueAlertRuleKey mapSeverityToRuleKey(IssueSeverity severity) {
        if (severity == null) {
            throw new IllegalArgumentException("Severity cannot be null");
        }
        return switch (severity) {
            case CRITICAL -> IssueAlertRuleKey.SEVERITY_CRITICAL;
            case HIGH -> IssueAlertRuleKey.SEVERITY_HIGH;
            case MEDIUM -> IssueAlertRuleKey.SEVERITY_MEDIUM;
            case LOW -> IssueAlertRuleKey.SEVERITY_LOW;
        };
    }

    /**
     * 프로젝트 Public ID 조회
     *
     * @param projectId 프로젝트 ID
     * @return Public ID
     */
    private String getProjectPublicId(UUID projectId) {
        return projectRepository.findById(projectId).map(Project::getPublicId).orElse("unknown");
    }

    /**
     * 이슈 URL 생성
     *
     * @param publicId 프로젝트 Public ID
     * @param issueId 이슈 ID
     * @return 이슈 URL
     */
    private String buildIssueUrl(String publicId, Long issueId) {
        return "/projects/" + publicId + "/issues/" + issueId + "/analysis";
    }
}

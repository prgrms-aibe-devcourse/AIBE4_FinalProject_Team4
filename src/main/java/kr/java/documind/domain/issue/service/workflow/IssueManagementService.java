package kr.java.documind.domain.issue.service.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.auth.service.ProjectQueryService;
import kr.java.documind.domain.issue.event.IssueStatusChangedEvent;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.IssuePriority;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.domain.issue.service.IssueNotificationService;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.repository.ProjectMemberRepository;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이슈 관리 통합 서비스
 *
 * <p>담당자 할당, 상태 전환, 이력 추적을 통합 관리
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IssueManagementService {

    private final IssueRepository issueRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueWorkflowValidator workflowValidator;
    private final IssueHistoryService historyService;
    private final ApplicationEventPublisher eventPublisher;
    private final IssueNotificationService notificationService;
    private final ProjectQueryService projectQueryService;

    /**
     * 이슈 담당자 지정
     *
     * @param issueId 이슈 ID
     * @param projectId 프로젝트 ID (권한 검증용)
     * @param assigneeId 담당자 멤버 ID
     * @param modifierId 변경 작업을 수행한 멤버 ID
     */
    @Transactional
    public void assignIssue(Long issueId, UUID projectId, UUID assigneeId, UUID modifierId) {
        Issue issue = getIssueAndVerifyProject(issueId, projectId);

        UUID beforeAssigneeId = issue.getAssigneeId();

        // 동일한 담당자로 변경 시도 시 예외
        if (beforeAssigneeId != null && beforeAssigneeId.equals(assigneeId)) {
            throw new BadRequestException("이미 해당 담당자로 지정되어 있습니다.");
        }

        // 담당자가 프로젝트 멤버인지 검증
        if (assigneeId != null
                && !projectMemberRepository.existsByProject_IdAndMember_IdAndStatus(
                        projectId, assigneeId, AccountStatus.ACTIVE)) {
            throw new BadRequestException("담당자가 프로젝트 멤버가 아닙니다: " + assigneeId);
        }

        // 담당자 할당
        issue.assignTo(assigneeId);

        // 이력 저장
        historyService.saveAssigneeChange(issueId, modifierId, beforeAssigneeId, assigneeId);

        // 담당자 변경 알림 발송
        if (assigneeId != null) {
            notificationService.notifyAssigneeChange(issue, assigneeId);
        }
    }

    /**
     * 이슈 상태 변경
     *
     * @param issueId 이슈 ID
     * @param projectId 프로젝트 ID (권한 검증용)
     * @param newStatus 변경할 상태
     * @param resolutionNote 해결 방법 설명 (RESOLVED 상태 시 선택 사항)
     * @param modifierId 변경 작업을 수행한 멤버 ID
     * @param includeInPatchNote 패치노트 반영 여부 (RESOLVED 상태 시에만 사용)
     */
    @Transactional
    public void updateIssueStatus(
            Long issueId,
            UUID projectId,
            IssueStatus newStatus,
            String resolutionNote,
            UUID modifierId,
            boolean includeInPatchNote) {
        Issue issue = getIssueAndVerifyProject(issueId, projectId);

        IssueStatus beforeStatus = issue.getStatus();

        // 상태 전환 규칙 검증
        workflowValidator.validateStatusTransition(beforeStatus, newStatus);

        // 상태 변경
        issue.changeStatus(newStatus);

        // 해결 방법 설명 저장 (RESOLVED 상태 시)
        if (newStatus == IssueStatus.RESOLVED
                && resolutionNote != null
                && !resolutionNote.isBlank()) {
            issue.writeResolutionNote(resolutionNote);
        }

        // 이력 저장
        historyService.saveStatusChange(
                issueId, modifierId, beforeStatus.getValue(), newStatus.getValue());

        // 상태 변경 이벤트 발행
        // - RESOLVED 전환 시 → IssueStatusChangedEventHandler.handleIssueResolved() 가 pending_item 적재
        // - RESOLVED → 다른 상태 전환 시 → handleIssueRollback() 이 벡터·pending_item 정리
        String projectPublicId = projectQueryService.getPublicIdByProjectId(issue.getProjectId());
        IssueStatusChangedEvent statusChangedEvent =
                new IssueStatusChangedEvent(
                        issue.getId(),
                        issue.getProjectId(),
                        projectPublicId,
                        beforeStatus,
                        newStatus,
                        !includeInPatchNote,
                        modifierId,
                        Instant.now());
        eventPublisher.publishEvent(statusChangedEvent);

        // 상태 변경 알림 발송
        notificationService.notifyStatusChange(issue, beforeStatus, newStatus);
    }

    /**
     * 이슈 우선순위 변경
     *
     * @param issueId 이슈 ID
     * @param projectId 프로젝트 ID
     * @param newPriority 변경할 우선순위
     * @param modifierId 변경자 멤버 ID
     */
    @Transactional
    public void updateIssuePriority(
            Long issueId, UUID projectId, IssuePriority newPriority, UUID modifierId) {
        Issue issue = getIssueAndVerifyProject(issueId, projectId);

        IssuePriority beforePriority = issue.getPriority();

        // 동일한 우선순위로 변경 시도 시 예외
        if (beforePriority == newPriority) {
            throw new BadRequestException("이미 해당 우선순위로 설정되어 있습니다.");
        }

        // 우선순위 변경
        issue.setPriority(newPriority);

        // 이력 저장
        historyService.savePriorityChange(issueId, modifierId, beforePriority, newPriority);
    }

    /**
     * 프로젝트별 이슈 목록 조회 (승인된 이슈만)
     *
     * <p>RECOMMENDED, REJECTED 상태는 제외하고 실제 이슈만 조회
     *
     * @param projectId 프로젝트 ID
     * @param status 필터링할 상태 (null이면 TODO/IN_PROGRESS/RESOLVED 전체)
     * @return 이슈 목록
     */
    public List<Issue> getIssueList(UUID projectId, IssueStatus status) {
        // 추천 상태를 명시적으로 요청한 경우 예외 처리
        if (status == IssueStatus.RECOMMENDED || status == IssueStatus.REJECTED) {
            throw new BadRequestException("추천 관련 상태는 IssueRecommendationService를 사용하세요.");
        }

        if (status == null) {
            // 전체 조회 시 실제 이슈만 (RECOMMENDED, REJECTED 제외) - DB 레벨 필터링
            return issueRepository.findByProjectIdAndStatusNotInOrderByCreatedAtDesc(
                    projectId, List.of(IssueStatus.RECOMMENDED, IssueStatus.REJECTED));
        }

        return issueRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status);
    }

    /**
     * 이슈 상세 조회
     *
     * @param issueId 이슈 ID
     * @param projectId 프로젝트 ID (권한 검증용)
     * @return 이슈 엔티티
     * @throws NotFoundException 이슈가 존재하지 않는 경우
     * @throws kr.java.documind.global.exception.ForbiddenException 다른 프로젝트의 이슈인 경우
     */
    public Issue getIssueDetail(Long issueId, UUID projectId) {
        return getIssueAndVerifyProject(issueId, projectId);
    }

    /**
     * 이슈 조회 및 프로젝트 권한 검증
     *
     * @param issueId 이슈 ID
     * @param projectId 프로젝트 ID
     * @return 이슈 엔티티
     * @throws NotFoundException 이슈가 존재하지 않는 경우
     * @throws kr.java.documind.global.exception.ForbiddenException 다른 프로젝트의 이슈인 경우
     */
    private Issue getIssueAndVerifyProject(Long issueId, UUID projectId) {
        Issue issue = getIssueOrThrow(issueId);

        if (!issue.getProjectId().equals(projectId)) {
            throw new kr.java.documind.global.exception.ForbiddenException(
                    "해당 프로젝트의 이슈가 아닙니다: " + issueId);
        }

        return issue;
    }

    /**
     * 이슈 조회 (존재하지 않으면 예외)
     *
     * @param issueId 이슈 ID
     * @return 이슈 엔티티
     * @throws NotFoundException 이슈가 존재하지 않는 경우
     */
    private Issue getIssueOrThrow(Long issueId) {
        return issueRepository
                .findById(issueId)
                .orElseThrow(() -> new NotFoundException("이슈를 찾을 수 없습니다: " + issueId));
    }
}

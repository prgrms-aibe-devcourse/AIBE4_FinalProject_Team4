package kr.java.documind.domain.issue.service.workflow;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.event.IssueResolvedEvent;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
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
    private final IssueWorkflowValidator workflowValidator;
    private final IssueHistoryService historyService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 이슈 담당자 지정
     *
     * @param issueId 이슈 ID
     * @param assigneeId 담당자 멤버 ID
     * @param modifierId 변경 작업을 수행한 멤버 ID
     */
    @Transactional
    public void assignIssue(Long issueId, UUID assigneeId, UUID modifierId) {
        Issue issue = getIssueOrThrow(issueId);

        UUID beforeAssigneeId = issue.getAssigneeId();

        // 담당자 할당
        issue.assignTo(assigneeId);

        // 이력 저장
        historyService.saveAssigneeChange(issueId, modifierId, beforeAssigneeId, assigneeId);
    }

    /**
     * 이슈 상태 변경
     *
     * @param issueId 이슈 ID
     * @param newStatus 변경할 상태
     * @param resolutionNote 해결 방법 설명 (RESOLVED 상태 시 선택 사항)
     * @param modifierId 변경 작업을 수행한 멤버 ID
     * @param includeInPatchNote 패치노트 반영 여부 (RESOLVED 상태 시에만 사용)
     */
    @Transactional
    public void updateIssueStatus(
            Long issueId,
            IssueStatus newStatus,
            String resolutionNote,
            UUID modifierId,
            boolean includeInPatchNote) {
        Issue issue = getIssueOrThrow(issueId);

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

        // RESOLVED 상태로 변경 시 이벤트 발행 (AI 패치노트 생성 트리거)
        if (newStatus == IssueStatus.RESOLVED && includeInPatchNote) {
            IssueResolvedEvent event =
                    new IssueResolvedEvent(
                            issue.getId(),
                            issue.getProjectId(),
                            issue.getTitle(),
                            issue.getDescription(),
                            issue.getFingerprint(),
                            issue.getResolvedAt(),
                            modifierId);
            eventPublisher.publishEvent(event);
        }
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
            throw new IllegalArgumentException("추천 관련 상태는 IssueRecommendationService를 사용하세요.");
        }

        if (status == null) {
            // 전체 조회 시 실제 이슈만 (RECOMMENDED, REJECTED 제외)
            return issueRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                    .filter(
                            issue ->
                                    issue.getStatus() != IssueStatus.RECOMMENDED
                                            && issue.getStatus() != IssueStatus.REJECTED)
                    .toList();
        }

        return issueRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status);
    }

    /**
     * 이슈 상세 조회
     *
     * @param issueId 이슈 ID
     * @return 이슈 엔티티
     * @throws NotFoundException 이슈가 존재하지 않는 경우
     */
    public Issue getIssueDetail(Long issueId) {
        return getIssueOrThrow(issueId);
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

package kr.java.documind.domain.issue.service.workflow;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.IssueHistory;
import kr.java.documind.domain.issue.model.repository.IssueHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이슈 변경 이력 관리 서비스
 *
 * <p>담당자, 상태, 우선순위 등 이슈 변경 사항을 추적하고 저장
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IssueHistoryService {

    private final IssueHistoryRepository issueHistoryRepository;

    /**
     * 상태 변경 이력 저장
     *
     * @param issueId 이슈 ID
     * @param modifierId 변경자 멤버 ID
     * @param beforeStatus 변경 전 상태
     * @param afterStatus 변경 후 상태
     * @return 저장된 이력
     */
    @Transactional
    public IssueHistory saveStatusChange(
            Long issueId, UUID modifierId, String beforeStatus, String afterStatus) {
        IssueHistory history =
                IssueHistory.ofStatusChange(issueId, modifierId, beforeStatus, afterStatus);
        return issueHistoryRepository.save(history);
    }

    /**
     * 담당자 변경 이력 저장
     *
     * @param issueId 이슈 ID
     * @param modifierId 변경자 멤버 ID
     * @param beforeAssignee 변경 전 담당자 ID (null 가능)
     * @param afterAssignee 변경 후 담당자 ID (null 가능)
     * @return 저장된 이력
     */
    @Transactional
    public IssueHistory saveAssigneeChange(
            Long issueId, UUID modifierId, UUID beforeAssignee, UUID afterAssignee) {
        IssueHistory history =
                IssueHistory.ofAssigneeChange(issueId, modifierId, beforeAssignee, afterAssignee);
        return issueHistoryRepository.save(history);
    }

    /**
     * 우선순위 변경 이력 저장
     *
     * @param issueId 이슈 ID
     * @param modifierId 변경자 멤버 ID
     * @param beforePriority 변경 전 우선순위
     * @param afterPriority 변경 후 우선순위
     * @return 저장된 이력
     */
    @Transactional
    public IssueHistory savePriorityChange(
            Long issueId, UUID modifierId, String beforePriority, String afterPriority) {
        IssueHistory history =
                IssueHistory.ofPriorityChange(issueId, modifierId, beforePriority, afterPriority);
        return issueHistoryRepository.save(history);
    }

    /**
     * 특정 이슈의 모든 변경 이력 조회 (최신순)
     *
     * @param issueId 이슈 ID
     * @return 이력 목록
     */
    public List<IssueHistory> getIssueHistories(Long issueId) {
        return issueHistoryRepository.findByIssueIdOrderByCreatedAtDesc(issueId);
    }

    /**
     * 특정 이슈의 특정 필드 변경 이력 조회
     *
     * @param issueId 이슈 ID
     * @param fieldName 필드명 (STATUS, ASSIGNEE, PRIORITY)
     * @return 이력 목록
     */
    public List<IssueHistory> getIssueHistoriesByField(Long issueId, String fieldName) {
        return issueHistoryRepository.findByIssueIdAndFieldNameOrderByCreatedAtDesc(
                issueId, fieldName);
    }

    /**
     * 특정 이슈의 최신 변경 이력 1건 조회
     *
     * @param issueId 이슈 ID
     * @return 최신 이력 (없으면 null)
     */
    public IssueHistory getLatestHistory(Long issueId) {
        return issueHistoryRepository.findTop1ByIssueIdOrderByCreatedAtDesc(issueId);
    }
}

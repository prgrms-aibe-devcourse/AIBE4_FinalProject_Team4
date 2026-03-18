package kr.java.documind.domain.issue.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import kr.java.documind.domain.issue.model.enums.IssuePriority;
import kr.java.documind.global.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이슈 변경 이력 엔티티
 *
 * <p>이슈의 담당자, 상태, 우선순위 등의 변경 이력을 추적
 */
@Entity(name = "issue_history")
@Table(name = "issue_history")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueHistory extends BaseEntity {

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "modifier_id", nullable = false)
    private UUID modifierId;

    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;

    @Column(name = "before_value", columnDefinition = "TEXT")
    private String beforeValue;

    @Column(name = "after_value", columnDefinition = "TEXT")
    private String afterValue;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 정적 팩토리 메서드
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 상태 변경 이력 생성
     *
     * @param issueId 이슈 ID
     * @param modifierId 변경자 ID
     * @param beforeStatus 변경 전 상태
     * @param afterStatus 변경 후 상태
     * @return 이력 객체
     */
    public static IssueHistory ofStatusChange(
            Long issueId, UUID modifierId, String beforeStatus, String afterStatus) {
        return IssueHistory.builder()
                .issueId(issueId)
                .modifierId(modifierId)
                .fieldName("STATUS")
                .beforeValue(beforeStatus)
                .afterValue(afterStatus)
                .build();
    }

    /**
     * 담당자 변경 이력 생성
     *
     * @param issueId 이슈 ID
     * @param modifierId 변경자 ID
     * @param beforeAssignee 변경 전 담당자 ID
     * @param afterAssignee 변경 후 담당자 ID
     * @return 이력 객체
     */
    public static IssueHistory ofAssigneeChange(
            Long issueId, UUID modifierId, UUID beforeAssignee, UUID afterAssignee) {
        return IssueHistory.builder()
                .issueId(issueId)
                .modifierId(modifierId)
                .fieldName("ASSIGNEE")
                .beforeValue(beforeAssignee != null ? beforeAssignee.toString() : null)
                .afterValue(afterAssignee != null ? afterAssignee.toString() : null)
                .build();
    }

    /**
     * 우선순위 변경 이력 생성
     *
     * @param issueId 이슈 ID
     * @param modifierId 변경자 ID
     * @param beforePriority 변경 전 우선순위
     * @param afterPriority 변경 후 우선순위
     * @return 이력 객체
     */
    public static IssueHistory ofPriorityChange(
            Long issueId,
            UUID modifierId,
            IssuePriority beforePriority,
            IssuePriority afterPriority) {
        return IssueHistory.builder()
                .issueId(issueId)
                .modifierId(modifierId)
                .fieldName("PRIORITY")
                .beforeValue(beforePriority != null ? beforePriority.toString() : null)
                .afterValue(afterPriority != null ? afterPriority.toString() : null)
                .build();
    }
}

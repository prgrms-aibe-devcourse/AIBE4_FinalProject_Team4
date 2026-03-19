package kr.java.documind.domain.notification.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import kr.java.documind.domain.notification.model.enums.IssueAlertRuleKey;
import kr.java.documind.global.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(
        name = "issue_alert_rule",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_issue_alert_rule",
                        columnNames = {"member_id", "project_id"}))
@Getter
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueAlertRule extends BaseEntity {

    @Column(name = "member_id", nullable = false, columnDefinition = "uuid")
    private UUID memberId;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(name = "notify_critical", nullable = false)
    private boolean notifyCritical;

    @Column(name = "notify_high", nullable = false)
    private boolean notifyHigh;

    @Column(name = "notify_medium", nullable = false)
    private boolean notifyMedium;

    @Column(name = "notify_low", nullable = false)
    private boolean notifyLow;

    @Column(name = "notify_assignee", nullable = false)
    private boolean notifyAssignee;

    @Column(name = "notify_status", nullable = false)
    private boolean notifyStatus;

    @Column(name = "notify_comment", nullable = false)
    private boolean notifyComment;

    public static IssueAlertRule createDefault(UUID projectId, UUID memberId) {
        IssueAlertRule r = new IssueAlertRule();
        r.projectId = projectId;
        r.memberId = memberId;
        r.notifyCritical = true;
        r.notifyHigh = true;
        r.notifyMedium = true;
        r.notifyLow = true;
        r.notifyAssignee = true;
        r.notifyStatus = true;
        r.notifyComment = true;
        return r;
    }

    public void update(IssueAlertRuleKey key, boolean active) {
        switch (key) {
            case SEVERITY_CRITICAL -> this.notifyCritical = active;
            case SEVERITY_HIGH -> this.notifyHigh = active;
            case SEVERITY_MEDIUM -> this.notifyMedium = active;
            case SEVERITY_LOW -> this.notifyLow = active;
            case ISSUE_ASSIGNED -> this.notifyAssignee = active;
            case ISSUE_STATUS_CHANGED -> this.notifyStatus = active;
            case ISSUE_MENTIONED -> this.notifyComment = active;
        }
    }

    public boolean isEnabled(IssueAlertRuleKey key) {
        return switch (key) {
            case SEVERITY_CRITICAL -> notifyCritical;
            case SEVERITY_HIGH -> notifyHigh;
            case SEVERITY_MEDIUM -> notifyMedium;
            case SEVERITY_LOW -> notifyLow;
            case ISSUE_ASSIGNED -> notifyAssignee;
            case ISSUE_STATUS_CHANGED -> notifyStatus;
            case ISSUE_MENTIONED -> notifyComment;
        };
    }
}

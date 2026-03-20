package kr.java.documind.domain.issue.model.enums;

public enum IssueAlertRuleKey {
    SEVERITY_CRITICAL("CRITICAL 심각도 이슈", "issue.severity == CRITICAL", RuleCategory.SEVERITY),
    SEVERITY_HIGH("HIGH 심각도 이슈", "issue.severity == HIGH", RuleCategory.SEVERITY),
    SEVERITY_MEDIUM("MEDIUM 심각도 이슈", "issue.severity == MEDIUM", RuleCategory.SEVERITY),
    SEVERITY_LOW("LOW 심각도 이슈", "issue.severity == LOW", RuleCategory.SEVERITY),

    ISSUE_ASSIGNED("담당자 배정/변경", "Assignee is changed", RuleCategory.ISSUE_EVENT),
    ISSUE_STATUS_CHANGED("진행 상태 변경", "Status is transitioned", RuleCategory.ISSUE_EVENT),
    ISSUE_MENTIONED("댓글 / 멘션", "Comment is added", RuleCategory.ISSUE_EVENT);

    private final String displayName;
    private final String condition;
    private final RuleCategory category;

    IssueAlertRuleKey(String displayName, String condition, RuleCategory category) {
        this.displayName = displayName;
        this.condition = condition;
        this.category = category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCondition() {
        return condition;
    }

    public RuleCategory getCategory() {
        return category;
    }

    public enum RuleCategory {
        SEVERITY,
        ISSUE_EVENT
    }
}

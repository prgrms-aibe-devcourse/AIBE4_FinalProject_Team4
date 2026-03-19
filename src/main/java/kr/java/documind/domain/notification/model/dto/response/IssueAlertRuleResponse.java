package kr.java.documind.domain.notification.model.dto.response;

import kr.java.documind.domain.notification.model.enums.IssueAlertRuleKey;

public record IssueAlertRuleResponse(
        String ruleKey, String name, String condition, String category, boolean active) {

    public static IssueAlertRuleResponse of(IssueAlertRuleKey key, boolean active) {
        return new IssueAlertRuleResponse(
                key.name(),
                key.getDisplayName(),
                key.getCondition(),
                key.getCategory().name(),
                active);
    }
}

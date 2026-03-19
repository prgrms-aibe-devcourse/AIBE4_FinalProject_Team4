package kr.java.documind.domain.notification.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.notification.model.dto.response.IssueAlertRuleResponse;
import kr.java.documind.domain.notification.model.entity.IssueAlertRule;
import kr.java.documind.domain.notification.model.enums.IssueAlertRuleKey;
import kr.java.documind.domain.notification.model.repository.IssueAlertRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IssueAlertRuleServcie {

    private final IssueAlertRuleRepository issueAlertRuleRepository;

    public List<IssueAlertRuleResponse> getRules(UUID projectId, UUID memberId) {
        Optional<IssueAlertRule> opt =
                issueAlertRuleRepository.findByProjectIdAndMemberId(projectId, memberId);

        if (opt.isEmpty()) {
            return Arrays.stream(IssueAlertRuleKey.values())
                    .map(key -> IssueAlertRuleResponse.of(key, true))
                    .toList();
        }

        IssueAlertRule rule = opt.get();
        return Arrays.stream(IssueAlertRuleKey.values())
                .map(key -> IssueAlertRuleResponse.of(key, rule.isEnabled(key)))
                .toList();
    }

    @Transactional
    public void updateRule(
            UUID projectId, UUID memberId, IssueAlertRuleKey ruleKey, boolean active) {
        IssueAlertRule rule =
                issueAlertRuleRepository
                        .findByProjectIdAndMemberId(projectId, memberId)
                        .orElseGet(() -> IssueAlertRule.createDefault(projectId, memberId));
        rule.update(ruleKey, active);
        issueAlertRuleRepository.save(rule);
    }
}

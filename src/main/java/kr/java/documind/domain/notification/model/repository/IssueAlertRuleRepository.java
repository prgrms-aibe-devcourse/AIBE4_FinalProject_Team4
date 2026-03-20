package kr.java.documind.domain.notification.model.repository;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.notification.model.entity.IssueAlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueAlertRuleRepository extends JpaRepository<IssueAlertRule, Long> {
    Optional<IssueAlertRule> findByProjectIdAndMemberId(UUID projectId, UUID memberId);
}

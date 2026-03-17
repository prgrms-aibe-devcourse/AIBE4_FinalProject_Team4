package kr.java.documind.domain.issue.model.repository;

import java.util.List;
import kr.java.documind.domain.issue.model.entity.IssueHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/** 이슈 변경 이력 Repository */
public interface IssueHistoryRepository extends JpaRepository<IssueHistory, Long> {

    /**
     * 특정 이슈의 모든 이력 조회 (최신순)
     *
     * @param issueId 이슈 ID
     * @return 이력 목록
     */
    List<IssueHistory> findByIssueIdOrderByCreatedAtDesc(Long issueId);

    /**
     * 특정 이슈의 특정 필드 이력 조회
     *
     * @param issueId 이슈 ID
     * @param fieldName 필드명 (STATUS, ASSIGNEE, PRIORITY)
     * @return 이력 목록
     */
    List<IssueHistory> findByIssueIdAndFieldNameOrderByCreatedAtDesc(
            Long issueId, String fieldName);

    /**
     * 특정 이슈의 최신 이력 1건 조회
     *
     * @param issueId 이슈 ID
     * @return 최신 이력
     */
    IssueHistory findTop1ByIssueIdOrderByCreatedAtDesc(Long issueId);
}

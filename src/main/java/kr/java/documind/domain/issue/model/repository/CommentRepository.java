package kr.java.documind.domain.issue.model.repository;

import java.util.List;
import kr.java.documind.domain.issue.model.entity.IssueComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<IssueComment, Long> {

    /**
     * 이슈별 댓글 목록 조회 (생성일 오름차순)
     *
     * @param issueId 이슈 ID
     * @return 댓글 목록 (오래된 순)
     */
    List<IssueComment> findByIssueIdOrderByCreatedAtAsc(Long issueId);

    /**
     * 이슈별 댓글 목록 조회 (생성일 오름차순) - 페이징
     *
     * @param issueId 이슈 ID
     * @param pageable 페이징 정보
     * @return 댓글 페이지 (오래된 순)
     */
    Page<IssueComment> findByIssueIdOrderByCreatedAtAsc(Long issueId, Pageable pageable);

    /**
     * 이슈별 댓글 개수 조회
     *
     * @param issueId 이슈 ID
     * @return 댓글 개수
     */
    long countByIssueId(Long issueId);

    /**
     * 댓글 삭제 (issueId와 commentId 검증)
     *
     * @param id 댓글 ID
     * @param issueId 이슈 ID
     */
    void deleteByIdAndIssueId(Long id, Long issueId);
}

package kr.java.documind.domain.issue.model.repository;

import java.util.List;
import java.util.Optional;
import kr.java.documind.domain.issue.model.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 이슈별 댓글 목록 조회 (생성일 오름차순)
     *
     * @param issueId 이슈 ID
     * @return 댓글 목록 (오래된 순)
     */
    List<Comment> findByIssueIdOrderByCreatedAtAsc(Long issueId);

    /**
     * 이슈별 댓글 개수 조회
     *
     * @param issueId 이슈 ID
     * @return 댓글 개수
     */
    long countByIssueId(Long issueId);

    /**
     * 댓글 ID로 조회 (프로젝트 소유권 검증용)
     *
     * <p>Issue와 조인하여 projectId 검증 가능
     *
     * @param commentId 댓글 ID
     * @return Comment (Issue 포함)
     */
    @Query(
            """
            SELECT c FROM comment c
            WHERE c.id = :commentId
            """)
    Optional<Comment> findByIdWithIssue(@Param("commentId") Long commentId);

    /**
     * 댓글 삭제 (issueId와 commentId 검증)
     *
     * @param id 댓글 ID
     * @param issueId 이슈 ID
     */
    void deleteByIdAndIssueId(Long id, Long issueId);
}

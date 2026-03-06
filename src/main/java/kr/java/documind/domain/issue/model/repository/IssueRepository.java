package kr.java.documind.domain.issue.model.repository;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueRepository extends JpaRepository<Issue, UUID> {

    /**
     * fingerprint와 projectId로 이슈 조회
     *
     * @param fingerprint SHA-256 해시
     * @param projectId 프로젝트 ID
     * @return 이슈 (존재하지 않으면 empty)
     */
    Optional<Issue> findByFingerprintAndProjectId(String fingerprint, UUID projectId);

    /**
     * fingerprint로 이슈 존재 여부 확인
     *
     * @param fingerprint SHA-256 해시
     * @return 존재하면 true
     */
    boolean existsByFingerprint(String fingerprint);
}

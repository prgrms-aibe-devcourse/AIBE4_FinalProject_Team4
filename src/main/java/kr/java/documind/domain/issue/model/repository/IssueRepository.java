package kr.java.documind.domain.issue.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

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

    /**
     * 프로젝트별 이슈 목록 조회 (최신순)
     *
     * @param projectId 프로젝트 ID
     * @return 이슈 목록
     */
    List<Issue> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    /**
     * 프로젝트별 특정 상태의 이슈 목록 조회 (최신순)
     *
     * @param projectId 프로젝트 ID
     * @param status 이슈 상태
     * @return 이슈 목록
     */
    List<Issue> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, IssueStatus status);

    /**
     * 프로젝트별 특정 상태의 이슈 개수
     *
     * @param projectId 프로젝트 ID
     * @param status 이슈 상태
     * @return 이슈 개수
     */
    long countByProjectIdAndStatus(UUID projectId, IssueStatus status);

    /**
     * 프로젝트 내에서 동일한 fingerprint를 가진 이슈 조회 (특정 상태 제외)
     *
     * @param projectId 프로젝트 ID
     * @param fingerprint 이슈 fingerprint
     * @param excludeStatus 제외할 상태 (RECOMMENDED 등)
     * @return 일치하는 이슈 (최신순)
     */
    Optional<Issue> findFirstByProjectIdAndFingerprintAndStatusNot(
            UUID projectId, String fingerprint, IssueStatus excludeStatus);

    /**
     * 프로젝트 내에서 같은 에러 타입의 이슈 목록 조회 (특정 상태 제외)
     *
     * @param projectId 프로젝트 ID
     * @param errorType 에러 타입
     * @param excludeStatus 제외할 상태 (RECOMMENDED 등)
     * @return 이슈 목록
     */
    List<Issue> findByProjectIdAndErrorTypeAndStatusNot(
            UUID projectId, ErrorType errorType, IssueStatus excludeStatus);

    /**
     * 프로젝트 내에서 같은 에러 타입의 최근 이슈 목록 조회 (특정 상태 제외, Top N)
     *
     * <p>성능 최적화: 유사도 계산 시 최근 N개만 비교하여 O(N) 복잡도 제한
     *
     * @param projectId 프로젝트 ID
     * @param errorType 에러 타입
     * @param excludeStatus 제외할 상태 (RECOMMENDED 등)
     * @param pageable 페이지 설정 (Top N)
     * @return 최근 이슈 목록 (최신순)
     */
    List<Issue> findByProjectIdAndErrorTypeAndStatusNotOrderByLastOccurredAtDesc(
            UUID projectId, ErrorType errorType, IssueStatus excludeStatus, Pageable pageable);

    /**
     * 프로젝트 내에서 같은 에러 타입의 해결된 이슈 목록 조회 (해결 노트가 있는 것만)
     *
     * @param projectId 프로젝트 ID
     * @param errorType 에러 타입
     * @param status 이슈 상태
     * @param pageable 페이지 설정
     * @return 해결된 이슈 목록
     */
    List<Issue> findByProjectIdAndErrorTypeAndStatusAndResolutionNoteNotNull(
            UUID projectId, ErrorType errorType, IssueStatus status, Pageable pageable);
}

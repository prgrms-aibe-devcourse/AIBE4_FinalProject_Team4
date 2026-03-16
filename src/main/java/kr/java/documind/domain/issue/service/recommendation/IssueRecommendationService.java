package kr.java.documind.domain.issue.service.recommendation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.issue.model.dto.response.SimilarityResult;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이슈 추천 서비스
 *
 * <p>로그 분석 결과 추천된 이슈를 관리 (승인/거부)
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IssueRecommendationService {

    private final IssueRepository issueRepository;
    private final IssueSimilarityCalculator similarityCalculator;

    /** 유사도 계산 시 비교할 최대 후보 이슈 수 (성능 최적화) */
    private static final int MAX_CANDIDATES_FOR_SIMILARITY = 100;

    /**
     * 추천 이슈 목록 조회
     *
     * @param projectId 프로젝트 ID
     * @return 추천 이슈 목록 (RECOMMENDED 상태)
     */
    public List<Issue> getRecommendationList(UUID projectId) {
        return issueRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(
                projectId, IssueStatus.RECOMMENDED);
    }

    /**
     * 추천 이슈 상세 조회
     *
     * @param issueId 이슈 ID
     * @return 추천 이슈
     */
    public Issue getRecommendationDetail(Long issueId) {
        Issue issue =
                issueRepository
                        .findById(issueId)
                        .orElseThrow(() -> new NotFoundException("추천 이슈를 찾을 수 없습니다: " + issueId));

        if (issue.getStatus() != IssueStatus.RECOMMENDED) {
            throw new ConflictException("추천 대기 상태(RECOMMENDED)가 아닙니다. 현재 상태: " + issue.getStatus());
        }

        return issue;
    }

    /**
     * 추천 이슈 승인 → 실제 이슈로 생성
     *
     * @param issueId 이슈 ID
     * @param assigneeId 담당자 ID (프로젝트 멤버)
     * @param title 이슈 제목 (수정된 제목, null이면 기존 제목 유지)
     * @param description 이슈 설명 (수정된 설명, null이면 기존 설명 유지)
     * @param modifierId 승인한 사용자 ID
     */
    @Transactional
    public void approveRecommendation(
            Long issueId, UUID assigneeId, String title, String description, UUID modifierId) {
        Issue issue = getRecommendationDetail(issueId);

        // 제목 및 설명 수정
        if (title != null && !title.isBlank()) {
            issue.updateTitle(title);
        }
        if (description != null && !description.isBlank()) {
            issue.updateDescription(description);
        }

        // 담당자 할당
        if (assigneeId != null) {
            issue.assignTo(assigneeId);
        }

        issue.approve(); // RECOMMENDED → TODO

        log.info(
                "Issue recommendation approved. issueId={}, assigneeId={}, modifierId={}, fingerprint={}, titleUpdated={}, descriptionUpdated={}",
                issueId,
                assigneeId,
                modifierId,
                issue.getFingerprint(),
                title != null,
                description != null);
    }

    /**
     * 추천 이슈 거부
     *
     * @param issueId 이슈 ID
     * @param modifierId 거부한 사용자 ID
     */
    @Transactional
    public void rejectRecommendation(Long issueId, UUID modifierId) {
        Issue issue = getRecommendationDetail(issueId);

        issue.reject(); // RECOMMENDED → REJECTED

        log.info(
                "Issue recommendation rejected. issueId={}, modifierId={}, fingerprint={}",
                issueId,
                modifierId,
                issue.getFingerprint());
    }

    /**
     * 추천 이슈 통계 조회 (대시보드용)
     *
     * @param projectId 프로젝트 ID
     * @return 추천 이슈 개수
     */
    public long getRecommendationCount(UUID projectId) {
        return issueRepository.countByProjectIdAndStatus(projectId, IssueStatus.RECOMMENDED);
    }

    /**
     * 추천 이슈와 기존 이슈 간의 유사도 분석
     *
     * <p>복합 접근 방식: - Fingerprint 완전 일치 확인 - Error Type 일치 확인 (20% 가중치) - Stack Trace Jaccard 유사도
     * (60% 가중치) - Message Levenshtein 유사도 (20% 가중치)
     *
     * <p>성능 최적화: 같은 ErrorType의 최근 100개 이슈만 비교하여 O(N) 복잡도 제한
     *
     * @param recommendedIssue 추천 이슈
     * @return 유사도 분석 결과 (최대 4개)
     */
    public List<SimilarityResult> analyzeSimilarity(Issue recommendedIssue) {
        UUID projectId = recommendedIssue.getProjectId();

        // 1. Fingerprint 완전 일치 확인 (최우선)
        Optional<Issue> exactMatch =
                issueRepository.findFirstByProjectIdAndFingerprintAndStatusNot(
                        projectId, recommendedIssue.getFingerprint(), IssueStatus.RECOMMENDED);

        if (exactMatch.isPresent()) {
            Issue matched = exactMatch.get();
            log.info(
                    "Exact fingerprint match found. recommendedIssue={}, matchedIssue={}",
                    recommendedIssue.getId(),
                    matched.getId());
            return List.of(SimilarityResult.exactMatch(matched.getId(), matched.getTitle()));
        }

        // 2. 같은 에러 타입의 최근 N개 이슈 조회 (RECOMMENDED 제외, 성능 최적화)
        List<Issue> candidates =
                issueRepository.findByProjectIdAndErrorTypeAndStatusNotOrderByLastOccurredAtDesc(
                        projectId,
                        recommendedIssue.getErrorType(),
                        IssueStatus.RECOMMENDED,
                        PageRequest.of(0, MAX_CANDIDATES_FOR_SIMILARITY));

        if (candidates.isEmpty()) {
            log.info("No similar issues found for recommendedIssue={}", recommendedIssue.getId());
            return List.of(SimilarityResult.noMatch());
        }

        log.debug(
                "Comparing recommendedIssue={} with {} recent candidates (errorType={})",
                recommendedIssue.getId(),
                candidates.size(),
                recommendedIssue.getErrorType());

        // 3. 각 후보와의 유사도 계산 후 상위 4개 선택
        List<SimilarityResult> topMatches =
                candidates.stream()
                        .map(
                                candidate ->
                                        similarityCalculator.calculate(recommendedIssue, candidate))
                        .filter(result -> !"NO_MATCH".equals(result.matchType()))
                        .sorted(Comparator.comparing(SimilarityResult::similarity).reversed())
                        .limit(4)
                        .toList();

        if (!topMatches.isEmpty()) {
            log.info(
                    "Found {} similar issues for recommendedIssue={}. Top similarity: {}%",
                    topMatches.size(), recommendedIssue.getId(), topMatches.get(0).similarity());
            return topMatches;
        }

        log.info(
                "No similar issues above threshold for recommendedIssue={}",
                recommendedIssue.getId());
        return List.of(SimilarityResult.noMatch());
    }
}

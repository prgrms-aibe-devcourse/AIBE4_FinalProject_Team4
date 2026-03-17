package kr.java.documind.domain.issue.service;

import kr.java.documind.domain.issue.model.dto.response.AffectedPlayerResponse;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.domain.logprocessor.model.repository.GameLogRepository;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 영향받은 플레이어 조회 서비스
 *
 * <p>특정 이슈로 인해 영향을 받은 플레이어 목록 및 통계 제공
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AffectedPlayersService {

    private final IssueRepository issueRepository;
    private final GameLogRepository gameLogRepository;

    /**
     * 이슈로 영향받은 플레이어 목록 조회
     *
     * @param issueId 이슈 ID
     * @param pageable 페이지네이션
     * @return 영향받은 플레이어 목록
     */
    public Page<AffectedPlayerResponse> getAffectedPlayers(Long issueId, Pageable pageable) {
        // 이슈 조회
        Issue issue =
                issueRepository
                        .findById(issueId)
                        .orElseThrow(() -> new NotFoundException("이슈를 찾을 수 없습니다: " + issueId));

        // 플레이어 통계 조회 (QueryDSL로 직접 DTO 변환)
        return gameLogRepository.findAffectedPlayersByFingerprint(
                issue.getFingerprint(), issue.getProjectId(), pageable);
    }
}

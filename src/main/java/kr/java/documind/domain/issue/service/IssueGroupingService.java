package kr.java.documind.domain.issue.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.domain.issue.service.fingerprint.FingerprintResult;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이슈 그룹핑 서비스
 *
 * <p>fingerprint 기반으로 로그를 이슈로 그룹핑
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IssueGroupingService {

    private final IssueRepository issueRepository;

    /**
     * 로그에 대한 이슈를 찾거나 생성
     *
     * <p>- 기존 이슈가 있으면 occurrence_count 증가 - 없으면 새 이슈 생성 - LOW/VERY_LOW 품질이면 REQUIRES_REVIEW 상태
     *
     * @param gameLog 게임 로그
     * @param fingerprintResult 핑거프린트 생성 결과
     * @return 찾아진 또는 생성된 이슈
     */
    @Transactional
    public Issue findOrCreateIssue(GameLog gameLog, FingerprintResult fingerprintResult) {
        String fingerprint = fingerprintResult.getFingerprint();
        UUID projectId = gameLog.getProjectId();

        try {
            // 기존 이슈 조회
            return issueRepository
                    .findByFingerprintAndProjectId(fingerprint, projectId)
                    .map(
                            existingIssue -> {
                                // 기존 이슈 발견 - occurrence_count 증가
                                existingIssue.incrementOccurrence(gameLog.getOccurredAt());
                                log.debug(
                                        "Existing issue found. issueId={}, fingerprint={}, occurrenceCount={}",
                                        existingIssue.getIssueId(),
                                        fingerprint,
                                        existingIssue.getOccurrenceCount());
                                return existingIssue;
                            })
                    .orElseGet(
                            () -> {
                                // 새 이슈 생성
                                Issue newIssue = createNewIssue(gameLog, fingerprintResult);
                                issueRepository.save(newIssue);
                                log.info(
                                        "New issue created. issueId={}, fingerprint={}, quality={}, status={}",
                                        newIssue.getIssueId(),
                                        fingerprint,
                                        fingerprintResult.getQuality(),
                                        newIssue.getStatus());
                                return newIssue;
                            });
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약 위반 (동시 생성 경쟁 상태)
            log.warn(
                    "UNIQUE constraint violation detected. Retrying with existing issue. fingerprint={}, projectId={}",
                    fingerprint,
                    projectId);

            // 재조회하여 기존 이슈 업데이트
            return issueRepository
                    .findByFingerprintAndProjectId(fingerprint, projectId)
                    .map(
                            existingIssue -> {
                                existingIssue.incrementOccurrence(gameLog.getOccurredAt());
                                log.info(
                                        "Recovered from race condition. issueId={}, occurrenceCount={}",
                                        existingIssue.getIssueId(),
                                        existingIssue.getOccurrenceCount());
                                return existingIssue;
                            })
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "Issue should exist after UNIQUE violation. fingerprint="
                                                    + fingerprint));
        }
    }

    /**
     * 새 이슈 생성
     *
     * @param gameLog 게임 로그
     * @param fingerprintResult 핑거프린트 생성 결과
     * @return 새로 생성된 이슈
     */
    private Issue createNewIssue(GameLog gameLog, FingerprintResult fingerprintResult) {
        OffsetDateTime now = OffsetDateTime.now();

        // 이슈 제목 생성 (archive에서 첫 줄 추출)
        String title = extractTitle(gameLog.getArchive());

        // 품질에 따른 상태 결정
        IssueStatus status =
                fingerprintResult.requiresReview() ? IssueStatus.REQUIRES_REVIEW : IssueStatus.OPEN;

        return Issue.builder()
                .issueId(UUID.randomUUID())
                .projectId(gameLog.getProjectId())
                .fingerprint(fingerprintResult.getFingerprint())
                .title(title)
                .status(status)
                .severity(gameLog.getSeverity())
                .fingerprintQuality(fingerprintResult.getQuality())
                .occurrenceCount(1L)
                .firstOccurredAt(gameLog.getOccurredAt())
                .lastOccurredAt(gameLog.getOccurredAt())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * archive에서 이슈 제목 추출
     *
     * <p>첫 줄을 제목으로 사용, 최대 500자
     *
     * @param archive 로그 본문
     * @return 이슈 제목
     */
    private String extractTitle(String archive) {
        if (archive == null || archive.isEmpty()) {
            return "Unknown Error";
        }

        String firstLine = archive.split("\\r?\\n")[0].trim();

        // 최대 500자로 제한
        if (firstLine.length() > 500) {
            return firstLine.substring(0, 497) + "...";
        }

        return firstLine;
    }
}

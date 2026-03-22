package kr.java.documind.domain.issue.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import kr.java.documind.domain.auth.model.enums.ProjectRole;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.domain.issue.service.fingerprint.FingerprintResult;
import kr.java.documind.domain.issue.service.severity.IssueSeverityService;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.member.model.repository.ProjectMemberRepository;
import kr.java.documind.global.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final IssueSeverityService issueSeverityService;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueNotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;

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
                                        existingIssue.getId(),
                                        fingerprint,
                                        existingIssue.getOccurrenceCount());

                                // 심각도 재계산 (발생 빈도 변경으로 점수 변경 가능)
                                issueSeverityService.calculateAndUpdateSeverity(
                                        existingIssue, gameLog);

                                return existingIssue;
                            })
                    .orElseGet(
                            () -> {
                                // 새 이슈 생성
                                Issue newIssue = createNewIssue(gameLog, fingerprintResult);
                                issueRepository.save(newIssue);
                                // 알림 FK 만족을 위해 domain_source(ISSUE) 동일 ID로 삽입
                                jdbcTemplate.update(
                                        "INSERT INTO domain_source (id, source_type, created_at, updated_at) VALUES (?, 'ISSUE', NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
                                        newIssue.getId());
                                log.debug(
                                        "New issue created. issueId={}, fingerprint={}, quality={}, status={}",
                                        newIssue.getId(),
                                        fingerprint,
                                        fingerprintResult.getQuality(),
                                        newIssue.getStatus());

                                // 신규 이슈 심각도 계산
                                issueSeverityService.calculateAndUpdateSeverity(newIssue, gameLog);

                                // 신규 이슈 생성 알림 발송
                                notificationService.notifyNewIssue(newIssue);

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
                                log.debug(
                                        "Recovered from race condition. issueId={}, occurrenceCount={}",
                                        existingIssue.getId(),
                                        existingIssue.getOccurrenceCount());

                                // 심각도 재계산
                                issueSeverityService.calculateAndUpdateSeverity(
                                        existingIssue, gameLog);

                                return existingIssue;
                            })
                    .orElseThrow(
                            () ->
                                    new ConflictException(
                                            "Issue should exist after UNIQUE violation. fingerprint="
                                                    + fingerprint));
        }
    }

    /**
     * 새 이슈 생성 (ERD 기준)
     *
     * @param gameLog 게임 로그
     * @param fingerprintResult 핑거프린트 생성 결과
     * @return 새로 생성된 이슈
     */
    private Issue createNewIssue(GameLog gameLog, FingerprintResult fingerprintResult) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID projectId = gameLog.getProjectId();

        // 이슈 제목 생성 (archive에서 첫 줄 추출)
        String title = extractTitle(gameLog.getArchive());

        // ErrorType 추론 (archive에서 예외 클래스명 파싱)
        ErrorType errorType = inferErrorType(gameLog.getArchive());

        // StackKey 생성 (archive에서 직접 추출)
        String stackKey = extractStackKeyFromArchive(gameLog.getArchive());

        // 프로젝트 관리자를 기본 담당자로 설정
        UUID defaultAssigneeId = findProjectManager(projectId);

        return Issue.builder()
                .assigneeId(defaultAssigneeId) // 프로젝트 관리자를 기본 담당자로 할당
                .projectId(projectId)
                .fingerprint(fingerprintResult.getFingerprint())
                .title(title)
                .status(IssueStatus.RECOMMENDED) // 추천 상태로 생성
                .errorType(errorType)
                .stackKey(stackKey)
                .occurrenceCount(1)
                .firstOccurredAt(gameLog.getOccurredAt())
                .lastOccurredAt(gameLog.getOccurredAt())
                .build();
    }

    /**
     * 프로젝트 관리자 조회
     *
     * @param projectId 프로젝트 ID
     * @return 프로젝트 관리자의 Member ID
     */
    private UUID findProjectManager(UUID projectId) {
        return projectMemberRepository
                .findByProject_IdAndProjectRole(projectId, ProjectRole.MANAGER)
                .stream()
                .findFirst()
                .map(pm -> pm.getMember().getId())
                .orElseThrow(
                        () -> new ConflictException("프로젝트 관리자를 찾을 수 없습니다. projectId=" + projectId));
    }

    /**
     * archive에서 ErrorType 추론
     *
     * @param archive 로그 본문
     * @return ErrorType
     */
    private ErrorType inferErrorType(String archive) {
        if (archive == null || archive.isEmpty()) {
            return ErrorType.UNKNOWN;
        }

        // 첫 줄에서 예외 클래스명 추출 (예: "NullPointerException: ...")
        String firstLine = archive.split("\\r?\\n")[0];
        return ErrorType.fromExceptionClassName(firstLine);
    }

    /**
     * archive에서 stackKey 추출
     *
     * <p>스택트레이스의 첫 번째 "at" 라인을 stackKey로 사용
     *
     * @param archive 로그 본문
     * @return 스택 키 (없으면 null)
     */
    private String extractStackKeyFromArchive(String archive) {
        if (archive == null || archive.isEmpty()) {
            return null;
        }

        // 스택트레이스에서 첫 번째 "at " 라인 찾기
        String[] lines = archive.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("at ")) {
                // "at com.example.Service.method(Service.java:42)" 형식
                // 최대 255자로 제한 (DB 컬럼 길이)
                return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
            }
        }

        return null;
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

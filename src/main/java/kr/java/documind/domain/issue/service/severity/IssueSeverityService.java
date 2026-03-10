package kr.java.documind.domain.issue.service.severity;

import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.vo.SeverityScore;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이슈 심각도 판별 Facade 서비스
 *
 * <p>SeverityCalculator를 사용하여 이슈의 심각도를 계산하고 Issue 엔티티에 반영
 *
 * <p>주요 기능:
 *
 * <ul>
 *   <li>신규 이슈 생성 시 심각도 자동 계산
 *   <li>기존 이슈 재발생 시 심각도 재계산
 *   <li>Issue 엔티티의 severity, severityScore 필드 업데이트
 * </ul>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IssueSeverityService {

    private final SeverityCalculator severityCalculator;

    /**
     * 이슈의 심각도를 계산하고 Issue 엔티티에 반영
     *
     * <p>트랜잭션 내에서 호출되어야 하며, Issue 엔티티는 영속 상태여야 함
     *
     * @param issue 심각도를 계산할 이슈 (영속 상태)
     * @param gameLog 이슈와 연관된 게임 로그
     * @return 계산된 심각도 점수 (SeverityScore VO)
     */
    @Transactional
    public SeverityScore calculateAndUpdateSeverity(Issue issue, GameLog gameLog) {
        if (issue == null) {
            throw new IllegalArgumentException("Issue는 null일 수 없습니다.");
        }

        if (gameLog == null) {
            throw new IllegalArgumentException("GameLog는 null일 수 없습니다.");
        }

        // 1. 심각도 점수 계산
        SeverityScore severityScore = severityCalculator.calculate(issue, gameLog);

        // 2. Issue 엔티티에 반영
        issue.updateSeverity(severityScore.getSeverity(), severityScore.getTotalScore());

        log.debug(
                "이슈 심각도 업데이트 완료: 이슈 ID={}, Fingerprint={}, Severity={}, Score={}/{}",
                issue.getId(),
                issue.getFingerprint(),
                severityScore.getSeverity().getValue(),
                severityScore.getTotalScore(),
                severityScore.getRawScore());

        return severityScore;
    }

    /**
     * 심각도 점수만 계산 (Issue 엔티티 업데이트 없음)
     *
     * <p>조회 전용 또는 미리보기 용도로 사용
     *
     * @param issue 심각도를 계산할 이슈
     * @param gameLog 이슈와 연관된 게임 로그
     * @return 계산된 심각도 점수 (SeverityScore VO)
     */
    public SeverityScore calculateSeverityOnly(Issue issue, GameLog gameLog) {
        if (issue == null) {
            throw new IllegalArgumentException("Issue는 null일 수 없습니다.");
        }

        if (gameLog == null) {
            throw new IllegalArgumentException("GameLog는 null일 수 없습니다.");
        }

        return severityCalculator.calculate(issue, gameLog);
    }
}

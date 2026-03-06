package kr.java.documind.domain.logprocessor.service;

import java.util.List;
import kr.java.documind.domain.issue.service.IssueGroupingService;
import kr.java.documind.domain.issue.service.fingerprint.FingerprintGenerator;
import kr.java.documind.domain.issue.service.fingerprint.FingerprintResult;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치 로그에 대한 이슈 그룹핑 서비스
 *
 * <p>logprocessor 도메인에서 issue 도메인의 서비스를 호출하는 어댑터 역할
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueGroupingBatchService {

    private final IssueGroupingService issueGroupingService;
    private final FingerprintGenerator fingerprintGenerator;

    /**
     * 로그 리스트를 이슈로 그룹핑
     *
     * <p>각 로그의 fingerprint로 이슈를 찾거나 생성
     *
     * @param logs 저장된 게임 로그 리스트
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void groupLogs(List<GameLog> logs) {
        for (GameLog gameLog : logs) {
            try {
                // fingerprint가 이미 GameLog에 있지만, quality 정보는 없으므로 다시 생성
                FingerprintResult fingerprintResult = fingerprintGenerator.generate(gameLog);

                // 이슈 찾거나 생성
                issueGroupingService.findOrCreateIssue(gameLog, fingerprintResult);
            } catch (Exception e) {
                log.error(
                        "Failed to group gameLog into issue. logId={}, fingerprint={}",
                        gameLog.getLogId(),
                        gameLog.getFingerprint(),
                        e);
                // 개별 로그 그룹핑 실패해도 다음 로그는 계속 처리
            }
        }

        log.info("Issue grouping completed for {} logs", logs.size());
    }
}

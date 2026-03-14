package kr.java.documind.domain.issue.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.java.documind.domain.issue.model.dto.response.DistributionDataResponse;
import kr.java.documind.domain.issue.model.dto.response.DistributionItem;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.repository.GameLogRepository;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이슈 분포 분석 서비스
 *
 * <p>OS, 앱 버전, 디바이스별 발생 분포를 분석
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DistributionAnalysisService {

    private final IssueRepository issueRepository;
    private final GameLogRepository gameLogRepository;

    /** 분포 분석에 사용할 최대 샘플 로그 수 */
    private static final int MAX_SAMPLE_SIZE = 200;

    /** 상위 N개 항목만 표시 (나머지는 "기타"로 묶음) */
    private static final int TOP_N_ITEMS = 5;

    /**
     * 이슈의 분포 분석 데이터 조회
     *
     * @param issueId 이슈 ID
     * @return 분포 분석 데이터
     */
    public DistributionDataResponse getDistributionData(Long issueId) {
        // 이슈 조회
        Issue issue =
                issueRepository
                        .findById(issueId)
                        .orElseThrow(() -> new NotFoundException("이슈를 찾을 수 없습니다: " + issueId));

        // 최근 로그 샘플 조회
        List<GameLog> sampleLogs =
                gameLogRepository.findRecentLogsByFingerprint(
                        issue.getFingerprint(), PageRequest.of(0, MAX_SAMPLE_SIZE));

        if (sampleLogs.isEmpty()) {
            log.warn("No logs found for fingerprint: {}", issue.getFingerprint());
            return new DistributionDataResponse(List.of(), List.of(), List.of());
        }

        // 분포 분석
        List<DistributionItem> osDistribution = analyzeOsDistribution(sampleLogs);
        List<DistributionItem> versionDistribution = analyzeVersionDistribution(sampleLogs);
        List<DistributionItem> deviceDistribution = analyzeDeviceDistribution(sampleLogs);

        return new DistributionDataResponse(
                osDistribution, versionDistribution, deviceDistribution);
    }

    /**
     * OS 분포 분석
     *
     * @param logs 게임 로그 목록
     * @return OS별 분포
     */
    private List<DistributionItem> analyzeOsDistribution(List<GameLog> logs) {
        return analyzeDistribution(
                logs, log -> extractFromResource(log, "os", "os.type", "os.name"), "기타 OS");
    }

    /**
     * 앱 버전 분포 분석
     *
     * @param logs 게임 로그 목록
     * @return 버전별 분포
     */
    private List<DistributionItem> analyzeVersionDistribution(List<GameLog> logs) {
        return analyzeDistribution(
                logs,
                log -> extractFromResource(log, "app.version", "service.version", "version"),
                "기타 버전");
    }

    /**
     * 디바이스 분포 분석
     *
     * @param logs 게임 로그 목록
     * @return 디바이스별 분포
     */
    private List<DistributionItem> analyzeDeviceDistribution(List<GameLog> logs) {
        return analyzeDistribution(
                logs,
                log ->
                        extractFromResource(
                                log,
                                "device",
                                "device.model",
                                "device.name",
                                "device.manufacturer"),
                "기타 디바이스");
    }

    /**
     * 공통 분포 분석 로직
     *
     * @param logs 게임 로그 목록
     * @param extractor 값 추출 함수
     * @param otherLabel "기타" 라벨
     * @return 분포 항목 목록
     */
    private List<DistributionItem> analyzeDistribution(
            List<GameLog> logs,
            java.util.function.Function<GameLog, String> extractor,
            String otherLabel) {

        int total = logs.size();

        // 그룹핑 및 카운팅
        Map<String, Long> countMap =
                logs.stream()
                        .map(extractor)
                        .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        // 상위 N개 선택
        List<Map.Entry<String, Long>> topEntries =
                countMap.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(TOP_N_ITEMS)
                        .toList();

        // 나머지를 "기타"로 묶기
        long topCount = topEntries.stream().mapToLong(Map.Entry::getValue).sum();
        long otherCount = total - topCount;

        List<DistributionItem> result =
                topEntries.stream()
                        .map(
                                entry ->
                                        DistributionItem.of(
                                                entry.getKey(), entry.getValue().intValue(), total))
                        .collect(Collectors.toList());

        // "기타" 추가 (있으면)
        if (otherCount > 0) {
            result.add(DistributionItem.of(otherLabel, (int) otherCount, total));
        }

        return result;
    }

    /**
     * Resource Map에서 값 추출 (여러 키 시도)
     *
     * @param log 게임 로그
     * @param keys 시도할 키 목록 (우선순위 순)
     * @return 추출된 값 또는 "알 수 없음"
     */
    private String extractFromResource(GameLog log, String... keys) {
        if (log.getResource() == null) {
            return "알 수 없음";
        }

        for (String key : keys) {
            Object value = log.getResource().get(key);
            if (value != null) {
                return value.toString();
            }
        }

        return "알 수 없음";
    }
}

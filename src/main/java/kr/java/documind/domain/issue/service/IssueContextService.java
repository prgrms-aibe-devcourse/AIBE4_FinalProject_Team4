package kr.java.documind.domain.issue.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.java.documind.domain.issue.model.dto.response.IssueContextResponse;
import kr.java.documind.domain.issue.model.dto.response.IssueContextResponse.EnvironmentInfo;
import kr.java.documind.domain.issue.model.dto.response.IssueContextResponse.GameStateExample;
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
 * 이슈 맥락 정보 서비스
 *
 * <p>이슈 발생 환경 및 게임 상태 분석
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IssueContextService {

    private final IssueRepository issueRepository;
    private final GameLogRepository gameLogRepository;

    /**
     * 이슈 발생 맥락 정보 조회
     *
     * @param issueId 이슈 ID
     * @return 맥락 정보
     */
    public IssueContextResponse getIssueContext(Long issueId) {
        // 이슈 조회
        Issue issue =
                issueRepository
                        .findById(issueId)
                        .orElseThrow(() -> new NotFoundException("이슈를 찾을 수 없습니다: " + issueId));

        // 최근 로그 샘플 조회 (최대 100개)
        List<GameLog> recentLogs =
                gameLogRepository.findRecentLogsByFingerprint(
                        issue.getFingerprint(), PageRequest.of(0, 100));

        if (recentLogs.isEmpty()) {
            // 로그가 없으면 기본값 반환
            return new IssueContextResponse(null, null, Map.of());
        }

        // 가장 빈번한 환경 정보 추출
        EnvironmentInfo environment = extractMostFrequentEnvironment(recentLogs);

        // 대표 게임 상태 예시 (최근 로그 중 첫 번째)
        GameStateExample gameState = extractGameStateExample(recentLogs.get(0));

        // 공통 속성 추출
        Map<String, String> commonAttributes = extractCommonAttributes(recentLogs);

        return new IssueContextResponse(environment, gameState, commonAttributes);
    }

    /**
     * 가장 빈번한 환경 정보 추출
     *
     * @param logs 로그 목록
     * @return 환경 정보
     */
    private EnvironmentInfo extractMostFrequentEnvironment(List<GameLog> logs) {
        // 환경 조합별 발생 횟수 집계
        Map<String, Long> envCounts =
                logs.stream()
                        .collect(
                                Collectors.groupingBy(
                                        log -> {
                                            String os = getResourceValue(log, "os", "Unknown OS");
                                            String device =
                                                    getResourceValue(
                                                            log, "device", "Unknown Device");
                                            String version =
                                                    getResourceValue(
                                                            log, "app.version", "Unknown Version");
                                            return os + "|" + device + "|" + version;
                                        },
                                        Collectors.counting()));

        // 가장 많이 발생한 환경 찾기
        Map.Entry<String, Long> mostFrequent =
                envCounts.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);

        if (mostFrequent == null) {
            return null;
        }

        String[] parts = mostFrequent.getKey().split("\\|");
        int percentage = (int) ((mostFrequent.getValue() * 100) / logs.size());

        return new EnvironmentInfo(parts[0], parts[1], parts[2], percentage);
    }

    /**
     * 게임 상태 예시 추출
     *
     * @param log 대표 로그
     * @return 게임 상태 예시
     */
    private GameStateExample extractGameStateExample(GameLog log) {
        Map<String, Object> attrs = log.getAttributes();

        if (attrs == null || attrs.isEmpty()) {
            return null;
        }

        // 주요 게임 상태 정보 추출
        String playerLevel = getAttributeValue(attrs, "player.level");
        String currentStage = getAttributeValue(attrs, "game.stage");
        String currency = getAttributeValue(attrs, "player.currency");

        // 나머지 속성들
        Map<String, Object> additionalState = new HashMap<>(attrs);
        additionalState.remove("player.level");
        additionalState.remove("game.stage");
        additionalState.remove("player.currency");

        return new GameStateExample(playerLevel, currentStage, currency, additionalState);
    }

    /**
     * 공통 속성 추출 (50% 이상의 로그에서 나타나는 속성)
     *
     * @param logs 로그 목록
     * @return 공통 속성
     */
    private Map<String, String> extractCommonAttributes(List<GameLog> logs) {
        Map<String, Map<Object, Long>> attributeValueCounts = new HashMap<>();

        // 각 속성별 값 분포 집계
        for (GameLog log : logs) {
            Map<String, Object> attrs = log.getAttributes();
            if (attrs == null) continue;

            for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                attributeValueCounts
                        .computeIfAbsent(key, k -> new HashMap<>())
                        .merge(value, 1L, Long::sum);
            }
        }

        // 50% 이상의 로그에서 동일한 값을 가진 속성만 추출
        Map<String, String> commonAttrs = new HashMap<>();
        long threshold = logs.size() / 2;

        for (Map.Entry<String, Map<Object, Long>> entry : attributeValueCounts.entrySet()) {
            String key = entry.getKey();
            Map.Entry<Object, Long> mostCommon =
                    entry.getValue().entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .orElse(null);

            if (mostCommon != null && mostCommon.getValue() > threshold) {
                commonAttrs.put(key, String.valueOf(mostCommon.getKey()));
            }
        }

        return commonAttrs;
    }

    /**
     * Resource 맵에서 값 추출
     *
     * @param log 로그
     * @param key 키
     * @param defaultValue 기본값
     * @return 값
     */
    private String getResourceValue(GameLog log, String key, String defaultValue) {
        Map<String, Object> resource = log.getResource();
        if (resource == null) return defaultValue;

        Object value = resource.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    /**
     * Attributes 맵에서 값 추출
     *
     * @param attrs 속성 맵
     * @param key 키
     * @return 값 (없으면 null)
     */
    private String getAttributeValue(Map<String, Object> attrs, String key) {
        Object value = attrs.get(key);
        return value != null ? String.valueOf(value) : null;
    }
}

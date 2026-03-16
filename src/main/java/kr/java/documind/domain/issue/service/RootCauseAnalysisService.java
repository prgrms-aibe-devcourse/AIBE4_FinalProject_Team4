package kr.java.documind.domain.issue.service;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.java.documind.domain.issue.model.dto.response.RootCauseAnalysisResponse;
import kr.java.documind.domain.issue.model.dto.response.RootCauseAnalysisResponse.PatternInfo;
import kr.java.documind.domain.issue.model.dto.response.RootCauseAnalysisResponse.SimilarResolution;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
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
 * 근본 원인 분석 서비스
 *
 * <p>규칙 기반 패턴 분석 및 원인 추론
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RootCauseAnalysisService {

    private final IssueRepository issueRepository;
    private final GameLogRepository gameLogRepository;

    /**
     * 이슈의 근본 원인 분석
     *
     * @param issueId 이슈 ID
     * @return 근본 원인 분석 결과
     */
    public RootCauseAnalysisResponse analyze(Long issueId) {
        // 이슈 조회
        Issue issue =
                issueRepository
                        .findById(issueId)
                        .orElseThrow(() -> new NotFoundException("이슈를 찾을 수 없습니다: " + issueId));

        // 최근 로그 샘플 조회 (최대 100개)
        List<GameLog> recentLogs =
                gameLogRepository.findRecentLogsByFingerprint(
                        issue.getFingerprint(),
                        issue.getProjectId(),
                        PageRequest.of(0, 100));

        // ErrorType 기반 원인 및 해결책
        ErrorType errorType = issue.getErrorType();
        List<String> possibleCauses = errorType.getPossibleCauses();
        List<String> solutions = errorType.getSolutions();

        // 패턴 분석
        List<PatternInfo> patterns = analyzePatterns(recentLogs);

        // 핵심 코드 위치 (stackKey)
        String hotspot = issue.getStackKey() != null ? issue.getStackKey() : "N/A";

        // 유사 해결 사례
        SimilarResolution similarResolution = findSimilarResolution(issue);

        return new RootCauseAnalysisResponse(
                errorType.getValue(),
                errorType.getDescription(),
                possibleCauses,
                solutions,
                patterns,
                hotspot,
                similarResolution);
    }

    /**
     * 패턴 분석
     *
     * @param logs 로그 목록
     * @return 발견된 패턴 목록
     */
    private List<PatternInfo> analyzePatterns(List<GameLog> logs) {
        List<PatternInfo> patterns = new ArrayList<>();

        if (logs.isEmpty()) {
            return patterns;
        }

        // 1. 시간 패턴 분석
        PatternInfo timePattern = analyzeTimePattern(logs);
        if (timePattern != null) {
            patterns.add(timePattern);
        }

        // 2. 요일 패턴 분석
        PatternInfo dayPattern = analyzeDayPattern(logs);
        if (dayPattern != null) {
            patterns.add(dayPattern);
        }

        // 3. 사용자 패턴 분석
        PatternInfo userPattern = analyzeUserPattern(logs);
        if (userPattern != null) {
            patterns.add(userPattern);
        }

        return patterns;
    }

    /**
     * 시간대 패턴 분석
     *
     * @param logs 로그 목록
     * @return 시간 패턴 정보
     */
    private PatternInfo analyzeTimePattern(List<GameLog> logs) {
        // 시간대별 발생 횟수 집계 (0-5시, 6-11시, 12-17시, 18-23시)
        Map<String, Long> hourRanges =
                logs.stream()
                        .collect(
                                Collectors.groupingBy(
                                        log -> {
                                            int hour =
                                                    log.getOccurredAt()
                                                            .atZoneSameInstant(ZoneOffset.UTC)
                                                            .getHour();
                                            if (hour >= 0 && hour < 6) return "00:00-06:00";
                                            if (hour >= 6 && hour < 12) return "06:00-12:00";
                                            if (hour >= 12 && hour < 18) return "12:00-18:00";
                                            return "18:00-24:00";
                                        },
                                        Collectors.counting()));

        // 가장 많이 발생한 시간대 찾기
        Map.Entry<String, Long> maxEntry =
                hourRanges.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);

        if (maxEntry != null && maxEntry.getValue() > logs.size() * 0.5) {
            long percentage = (maxEntry.getValue() * 100) / logs.size();
            return new PatternInfo(
                    "TIME",
                    String.format("주로 %s 시간대에 발생 (전체의 %d%%)", maxEntry.getKey(), percentage));
        }

        return null;
    }

    /**
     * 요일 패턴 분석
     *
     * @param logs 로그 목록
     * @return 요일 패턴 정보
     */
    private PatternInfo analyzeDayPattern(List<GameLog> logs) {
        Map<Boolean, Long> weekdayWeekend =
                logs.stream()
                        .collect(
                                Collectors.groupingBy(
                                        log -> {
                                            DayOfWeek day =
                                                    log.getOccurredAt()
                                                            .atZoneSameInstant(ZoneOffset.UTC)
                                                            .getDayOfWeek();
                                            return day == DayOfWeek.SATURDAY
                                                    || day == DayOfWeek.SUNDAY;
                                        },
                                        Collectors.counting()));

        Long weekendCount = weekdayWeekend.getOrDefault(true, 0L);
        Long weekdayCount = weekdayWeekend.getOrDefault(false, 0L);

        // 주말/평일 편향이 심한 경우
        if (weekendCount > logs.size() * 0.7) {
            return new PatternInfo("DAY", "주로 주말에 발생 (트래픽 증가와 연관 가능성)");
        } else if (weekdayCount > logs.size() * 0.7) {
            return new PatternInfo("DAY", "주로 평일에 발생 (업무 시간대 부하와 연관 가능성)");
        }

        return null;
    }

    /**
     * 사용자 패턴 분석
     *
     * @param logs 로그 목록
     * @return 사용자 패턴 정보
     */
    private PatternInfo analyzeUserPattern(List<GameLog> logs) {
        long uniqueUsers = logs.stream().filter(log -> log.getUserId() != null).distinct().count();

        long totalLogs = logs.size();

        // 같은 사용자가 반복적으로 발생시키는 경우
        if (uniqueUsers > 0 && totalLogs / uniqueUsers > 5) {
            return new PatternInfo(
                    "USER",
                    String.format(
                            "특정 사용자 %d명이 반복적으로 발생 (사용자당 평균 %.1f회)",
                            uniqueUsers, (double) totalLogs / uniqueUsers));
        }

        return null;
    }

    /**
     * 유사 해결 사례 찾기
     *
     * @param issue 현재 이슈
     * @return 유사 해결 사례
     */
    private SimilarResolution findSimilarResolution(Issue issue) {
        // 같은 ErrorType을 가진 해결된 이슈 중 해결 노트가 있는 것 찾기
        List<Issue> resolvedSimilarIssues =
                issueRepository.findByProjectIdAndErrorTypeAndStatusAndResolutionNoteNotNull(
                        issue.getProjectId(),
                        issue.getErrorType(),
                        IssueStatus.RESOLVED,
                        PageRequest.of(0, 1));

        if (!resolvedSimilarIssues.isEmpty()) {
            Issue similarIssue = resolvedSimilarIssues.get(0);
            return new SimilarResolution(
                    similarIssue.getId(),
                    similarIssue.getTitle(),
                    similarIssue.getResolutionNote());
        }

        return null;
    }
}

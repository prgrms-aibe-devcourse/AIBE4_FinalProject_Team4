package kr.java.documind.domain.issue.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.java.documind.domain.issue.model.dto.response.OccurrenceTrendResponse;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.domain.logprocessor.model.repository.GameLogRepository;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이슈 발생 추이 조회 서비스
 *
 * <p>시간대별 이슈 발생 통계 제공
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OccurrenceTrendService {

    private final IssueRepository issueRepository;
    private final GameLogRepository gameLogRepository;

    /**
     * 이슈 발생 추이 조회
     *
     * @param issueId 이슈 ID
     * @param days 조회 기간 (일 단위)
     * @return 날짜별 발생 횟수
     */
    public List<OccurrenceTrendResponse> getOccurrenceTrend(Long issueId, int days) {
        // 이슈 조회
        Issue issue =
                issueRepository
                        .findById(issueId)
                        .orElseThrow(() -> new NotFoundException("이슈를 찾을 수 없습니다: " + issueId));

        // 날짜 범위 계산 (UTC)
        OffsetDateTime endDate =
                OffsetDateTime.now(ZoneOffset.UTC)
                        .plusDays(1)
                        .withHour(0)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0);
        OffsetDateTime startDate = endDate.minusDays(days);

        // 데이터 조회
        List<OccurrenceTrendResponse> rawData =
                gameLogRepository.findOccurrenceTrendByFingerprint(
                        issue.getFingerprint(), issue.getProjectId(), startDate, endDate);

        // 데이터가 없는 날짜는 0으로 채우기
        return fillMissingDates(
                rawData, startDate.toLocalDate(), endDate.toLocalDate().minusDays(1));
    }

    /**
     * 데이터가 없는 날짜를 0으로 채움
     *
     * @param data 실제 데이터
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 모든 날짜가 포함된 데이터
     */
    private List<OccurrenceTrendResponse> fillMissingDates(
            List<OccurrenceTrendResponse> data, LocalDate startDate, LocalDate endDate) {

        // 데이터를 Map으로 변환 (날짜 -> 횟수)
        Map<LocalDate, Long> dataMap =
                data.stream()
                        .collect(
                                Collectors.toMap(
                                        OccurrenceTrendResponse::date,
                                        OccurrenceTrendResponse::count));

        // 모든 날짜에 대해 데이터 생성 (없으면 0)
        List<OccurrenceTrendResponse> result = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            Long count = dataMap.getOrDefault(currentDate, 0L);
            result.add(new OccurrenceTrendResponse(currentDate, count));
            currentDate = currentDate.plusDays(1);
        }

        return result;
    }
}

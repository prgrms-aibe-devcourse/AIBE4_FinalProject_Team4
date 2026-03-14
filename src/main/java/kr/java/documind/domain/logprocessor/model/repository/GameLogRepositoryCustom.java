package kr.java.documind.domain.logprocessor.model.repository;

import java.time.OffsetDateTime;
import java.util.List;
import kr.java.documind.domain.issue.model.dto.response.AffectedPlayerResponse;
import kr.java.documind.domain.issue.model.dto.response.OccurrenceTrendResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * GameLog Custom Repository 인터페이스
 *
 * <p>QueryDSL을 사용한 복잡한 쿼리 메서드 정의
 */
public interface GameLogRepositoryCustom {

    /**
     * 특정 fingerprint로 영향받은 플레이어 통계 조회 (QueryDSL)
     *
     * @param fingerprint 이슈 fingerprint
     * @param pageable 페이지네이션
     * @return 플레이어별 통계
     */
    Page<AffectedPlayerResponse> findAffectedPlayersByFingerprint(
            String fingerprint, Pageable pageable);

    /**
     * 특정 fingerprint의 발생 추이 조회 (QueryDSL)
     *
     * @param fingerprint 이슈 fingerprint
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 날짜별 발생 횟수
     */
    List<OccurrenceTrendResponse> findOccurrenceTrendByFingerprint(
            String fingerprint, OffsetDateTime startDate, OffsetDateTime endDate);
}

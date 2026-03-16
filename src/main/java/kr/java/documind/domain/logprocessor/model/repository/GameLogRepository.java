package kr.java.documind.domain.logprocessor.model.repository;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.entity.GameLogId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * GameLog Repository
 *
 * <p>분포 분석 및 영향받은 플레이어 조회 메서드 제공
 */
public interface GameLogRepository
        extends JpaRepository<GameLog, GameLogId>, GameLogRepositoryCustom {

    /**
     * 특정 fingerprint의 최근 로그 조회 (분포 분석용)
     *
     * @param fingerprint 이슈 fingerprint
     * @param projectId 프로젝트 ID
     * @param pageable 페이징 (최대 개수 제한)
     * @return 게임 로그 목록
     */
    @Query(
            "SELECT g FROM game_log g WHERE g.fingerprint = :fingerprint AND g.projectId = :projectId ORDER BY g.occurredAt DESC")
    List<GameLog> findRecentLogsByFingerprint(
            @Param("fingerprint") String fingerprint,
            @Param("projectId") UUID projectId,
            Pageable pageable);
}

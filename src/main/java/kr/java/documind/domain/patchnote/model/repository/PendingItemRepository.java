package kr.java.documind.domain.patchnote.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.global.enums.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PendingItemRepository
        extends JpaRepository<PendingItem, Long>, PendingItemRepositoryCustom {

    // 단건 조회
    Optional<PendingItem> findByProjectIdAndSourceTypeAndSourceId(
            UUID projectId, SourceType sourceType, Long sourceId);

    // RAG pre-filter — ID 목록 기반 활성 항목 조회
    @Query(
            """
            SELECT p FROM PendingItem p
            WHERE p.projectId = :projectId
              AND p.id IN :ids
              AND p.sourceDeleted = false
            """)
    List<PendingItem> findAllByProjectIdAndIdInAndNotDeleted(
            @Param("projectId") UUID projectId, @Param("ids") List<Long> ids);

    // 초안 생성용 — 프로젝트의 PENDING 상태 항목 전체 조회 (원본 삭제 포함, sourceDeleted 필터는 서비스에서)
    @Query(
            """
            SELECT p FROM PendingItem p
            WHERE p.projectId = :projectId
              AND p.status    = :status
            ORDER BY p.sourceCreatedAt DESC
            """)
    List<PendingItem> findByProjectIdAndStatus(
            @Param("projectId") UUID projectId, @Param("status") PendingItemStatus status);

    // 상태 일괄 변경
    @Modifying
    @Query(
            """
            UPDATE PendingItem p
            SET p.status = 'COMPLETED'
            WHERE p.projectId = :projectId
              AND p.id IN :ids
              AND p.status = 'PENDING'
            """)
    void markCompleted(@Param("projectId") UUID projectId, @Param("ids") List<Long> ids);

    // 원본 삭제 처리
    @Modifying
    @Query(
            """
            UPDATE PendingItem p
            SET p.sourceDeleted = true
            WHERE p.projectId  = :projectId
              AND p.sourceType = :sourceType
              AND p.sourceId   = :sourceId
            """)
    void markSourceDeleted(
            @Param("projectId") UUID projectId,
            @Param("sourceType") SourceType sourceType,
            @Param("sourceId") Long sourceId);

    // 이슈 롤백 시 hard delete
    @Modifying
    @Query(
            """
            DELETE FROM PendingItem p
            WHERE p.projectId  = :projectId
              AND p.sourceType = :sourceType
              AND p.sourceId   = :sourceId
              AND p.status    != 'COMPLETED'
            """)
    void deleteByProjectIdAndSourceTypeAndSourceIdIfNotCompleted(
            @Param("projectId") UUID projectId,
            @Param("sourceType") SourceType sourceType,
            @Param("sourceId") Long sourceId);

    boolean existsByProjectIdAndSourceTypeAndSourceId(
            UUID projectId, SourceType sourceType, Long sourceId);
}

package kr.java.documind.domain.patchnote.model.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.global.enums.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PendingItemRepository extends JpaRepository<PendingItem, Long> {

    Optional<PendingItem> findByProjectIdAndSourceTypeAndSourceId(
            UUID projectId, SourceType sourceType, Long sourceId);

    @Query(
            """
            SELECT p FROM PendingItem p
            WHERE p.projectId = :projectId
              AND (
                  p.status = 'PENDING'
                  OR (:includeExcluded = true AND p.status = 'EXCLUDED')
                  OR (:includeCompleted = true AND p.status = 'COMPLETED')
              )
              AND (:sourceType IS NULL OR p.sourceType = :sourceType)
              AND (:patchType  IS NULL OR p.patchType  = :patchType)
              AND (:from IS NULL OR p.sourceCreatedAt >= :from)
              AND (:to   IS NULL OR p.sourceCreatedAt  < :to)
              AND (
                  :keyword IS NULL
                  OR LOWER(p.title)    LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(p.summary)  LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR p.choseong        LIKE CONCAT('%', :keyword, '%')
              )
            ORDER BY p.sourceCreatedAt DESC, p.id DESC
            """)
    List<PendingItem> findFeed(
            @Param("projectId") UUID projectId,
            @Param("sourceType") SourceType sourceType,
            @Param("patchType") PatchType patchType,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("keyword") String keyword,
            @Param("includeExcluded") boolean includeExcluded,
            @Param("includeCompleted") boolean includeCompleted);

    @Query(
            """
            SELECT p FROM PendingItem p
            WHERE p.projectId = :projectId
              AND p.id IN :ids
              AND p.sourceDeleted = false
            """)
    List<PendingItem> findAllByProjectIdAndIdInAndNotDeleted(
            @Param("projectId") UUID projectId, @Param("ids") List<Long> ids);

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

    @Modifying
    @Query(
            """
            UPDATE PendingItem p
            SET p.sourceDeleted = true
            WHERE p.projectId = :projectId
              AND p.sourceType = :sourceType
              AND p.sourceId   = :sourceId
            """)
    void markSourceDeleted(
            @Param("projectId") UUID projectId,
            @Param("sourceType") SourceType sourceType,
            @Param("sourceId") Long sourceId);

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

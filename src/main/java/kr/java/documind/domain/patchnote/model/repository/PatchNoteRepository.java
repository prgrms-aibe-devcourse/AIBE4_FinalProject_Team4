package kr.java.documind.domain.patchnote.model.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.entity.PatchNote;
import kr.java.documind.domain.patchnote.model.enums.PatchNoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PatchNoteRepository extends JpaRepository<PatchNote, Long> {

    // soft delete 제외
    Optional<PatchNote> findByIdAndDeletedAtIsNull(Long id);

    // 버전으로 조회 (중복 확인용)
    Optional<PatchNote> findByProjectIdAndMajorVersionAndMinorVersionAndPatchVersion(
            UUID projectId, Integer majorVersion, Integer minorVersion, Integer patchVersion);

    // 프로젝트 내 활성 패치노트 목록 (최신순)
    @Query(
            """
            SELECT p FROM PatchNote p
            WHERE p.projectId  = :projectId
              AND p.deletedAt IS NULL
            ORDER BY p.createdAt DESC
            """)
    List<PatchNote> findAllByProjectIdAndNotDeleted(@Param("projectId") UUID projectId);

    // 상태별 조회
    @Query(
            """
            SELECT p FROM PatchNote p
            WHERE p.projectId = :projectId
              AND p.status    = :status
              AND p.deletedAt IS NULL
            ORDER BY p.createdAt DESC
            """)
    List<PatchNote> findAllByProjectIdAndStatus(
            @Param("projectId") UUID projectId, @Param("status") PatchNoteStatus status);

    @Query(
            """
            SELECT COUNT(p) > 0 FROM PatchNote p
            WHERE p.projectId    = :projectId
              AND p.majorVersion = :majorVersion
              AND p.minorVersion = :minorVersion
              AND p.patchVersion = :patchVersion
              AND p.deletedAt IS NULL
            """)
    boolean existsByVersionAndNotDeleted(
            @Param("projectId") UUID projectId,
            @Param("majorVersion") Integer majorVersion,
            @Param("minorVersion") Integer minorVersion,
            @Param("patchVersion") Integer patchVersion);

    @Modifying
    @Query(
            """
            UPDATE PatchNote p
            SET p.status    = 'DELETED',
                p.deletedAt = :deletedAt
            WHERE p.id        = :id
              AND p.projectId = :projectId
              AND p.deletedAt IS NULL
            """)
    int softDelete(
            @Param("id") Long id,
            @Param("projectId") UUID projectId,
            @Param("deletedAt") OffsetDateTime deletedAt);

    @Modifying
    @Query(
            """
            UPDATE PatchNote p
            SET p.status    = 'DELETED',
                p.deletedAt = :deletedAt
            WHERE p.projectId    = :projectId
              AND p.majorVersion = :majorVersion
              AND p.minorVersion = :minorVersion
              AND p.patchVersion = :patchVersion
              AND p.deletedAt IS NULL
            """)
    int softDeleteByVersion(
            @Param("projectId") UUID projectId,
            @Param("majorVersion") Integer majorVersion,
            @Param("minorVersion") Integer minorVersion,
            @Param("patchVersion") Integer patchVersion,
            @Param("deletedAt") OffsetDateTime deletedAt);

    // 다건 soft delete
    @Modifying
    @Query(
            """
            UPDATE PatchNote p
            SET p.status    = 'DELETED',
                p.deletedAt = :deletedAt
            WHERE p.id        IN :ids
              AND p.projectId  = :projectId
              AND p.deletedAt IS NULL
            """)
    int softDeleteAll(
            @Param("ids") List<Long> ids,
            @Param("projectId") UUID projectId,
            @Param("deletedAt") OffsetDateTime deletedAt);
}

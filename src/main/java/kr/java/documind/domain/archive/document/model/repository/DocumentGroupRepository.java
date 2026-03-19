package kr.java.documind.domain.archive.document.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentGroupRepository extends JpaRepository<DocumentGroup, Long> {

    Optional<DocumentGroup> findByIdAndProjectId(Long id, UUID projectId);

    @Query(
            "SELECT g.id AS groupId, g.groupName AS groupName, g.category AS category, "
                    + "MAX(dm.majorVersion * 1000000 + dm.minorVersion * 1000 + dm.patchVersion) AS versionOrdinal, "
                    + "COUNT(dm) AS documentCount "
                    + "FROM DocumentGroup g JOIN DocumentMetadata dm ON dm.documentGroup = g "
                    + "WHERE g.projectId = :projectId "
                    + "GROUP BY g.id, g.groupName, g.category")
    Page<DocumentGroupSummary> findGroupSummariesByProjectId(
            @Param("projectId") UUID projectId, Pageable pageable);

    @Query(
            "SELECT g.id AS groupId, g.groupName AS groupName, g.category AS category, "
                    + "MAX(dm.majorVersion * 1000000 + dm.minorVersion * 1000 + dm.patchVersion) AS versionOrdinal, "
                    + "COUNT(dm) AS documentCount "
                    + "FROM DocumentGroup g JOIN DocumentMetadata dm ON dm.documentGroup = g "
                    + "WHERE g.projectId = :projectId "
                    + "AND (LOWER(g.groupName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
                    + "  OR g.choseong LIKE CONCAT('%', :choseong, '%')) "
                    + "GROUP BY g.id, g.groupName, g.category")
    Page<DocumentGroupSummary> findGroupSummariesByProjectIdAndKeyword(
            @Param("projectId") UUID projectId,
            @Param("keyword") String keyword,
            @Param("choseong") String choseong,
            Pageable pageable);

    @Query("SELECT DISTINCT dg.groupName FROM DocumentGroup dg WHERE dg.projectId = :projectId")
    List<String> findDistinctGroupNamesByProjectId(@Param("projectId") UUID projectId);

    @Query("SELECT DISTINCT dg.category FROM DocumentGroup dg WHERE dg.projectId = :projectId")
    List<String> findDistinctCategoriesByProjectId(@Param("projectId") UUID projectId);

    boolean existsByProjectIdAndCategoryAndGroupName(
            UUID projectId, String category, String groupName);
}

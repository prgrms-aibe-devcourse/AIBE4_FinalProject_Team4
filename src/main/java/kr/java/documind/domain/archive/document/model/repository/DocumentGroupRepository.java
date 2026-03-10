package kr.java.documind.domain.archive.document.model.repository;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentGroupRepository extends JpaRepository<DocumentGroup, Long> {

    @Query(
            "SELECT g.id AS groupId, g.groupName AS groupName, g.category AS category, "
                    + "MAX(dm.majorVersion * 1000000 + dm.minorVersion * 1000 + dm.patchVersion) AS versionOrdinal, "
                    + "COUNT(dm) AS documentCount "
                    + "FROM DocumentGroup g JOIN DocumentMetadata dm ON dm.documentGroup = g "
                    + "WHERE g.project.id = :projectId "
                    + "GROUP BY g.id, g.groupName, g.category")
    Page<DocumentGroupSummary> findGroupSummariesByProjectId(
            @Param("projectId") UUID projectId, Pageable pageable);

    Optional<DocumentGroup> findByIdAndProject_Id(Long id, UUID projectId);

    boolean existsByProject_IdAndCategoryAndGroupName(
            UUID projectId, String category, String groupName);
}

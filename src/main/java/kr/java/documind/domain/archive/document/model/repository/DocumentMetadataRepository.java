package kr.java.documind.domain.archive.document.model.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {

    Optional<DocumentMetadata> findByIdAndDocumentGroupProjectId(Long id, UUID projectId);

    List<DocumentMetadata>
    findByDocumentGroupOrderByMajorVersionDescMinorVersionDescPatchVersionDesc(
        DocumentGroup documentGroup);

    long countByDocumentGroup(DocumentGroup documentGroup);

    List<DocumentMetadata> findByIdIn(Collection<Long> ids);

    boolean existsByDocumentGroupAndMajorVersionAndMinorVersionAndPatchVersion(
        DocumentGroup documentGroup, int majorVersion, int minorVersion, int patchVersion);

    @Query(
        "SELECT CASE WHEN COUNT(dm) > 0 THEN true ELSE false END "
            + "FROM DocumentMetadata dm "
            + "WHERE dm.documentGroup.projectId = :projectId AND dm.hash = :hash")
    boolean existsByProjectIdAndHash(
        @Param("projectId") UUID projectId, @Param("hash") String hash);

    @Query(
        "SELECT dm.id FROM DocumentMetadata dm "
            + "WHERE dm.documentGroup.projectId = :projectId "
            + "AND dm.documentGroup.groupName = :groupName")
    List<Long> findIdsByProjectIdAndGroupName(
        @Param("projectId") UUID projectId, @Param("groupName") String groupName);

    @Query(
        "SELECT dm.id FROM DocumentMetadata dm "
            + "WHERE dm.documentGroup.projectId = :projectId "
            + "AND dm.documentGroup.category = :categoryName")
    List<Long> findIdsByProjectIdAndCategoryName(
        @Param("projectId") UUID projectId, @Param("categoryName") String categoryName);
}

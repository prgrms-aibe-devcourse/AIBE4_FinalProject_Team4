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

    @Query(
            "SELECT dm FROM DocumentMetadata dm "
                    + "WHERE dm.documentGroup = :documentGroup "
                    + "ORDER BY dm.majorVersion DESC, dm.minorVersion DESC, dm.patchVersion DESC")
    List<DocumentMetadata> findVersionsByGroup(@Param("documentGroup") DocumentGroup documentGroup);

    List<DocumentMetadata> findByIdIn(Collection<Long> ids);

    long countByDocumentGroup(DocumentGroup documentGroup);

    @Query(
            "SELECT CASE WHEN COUNT(dm) > 0 THEN true ELSE false END "
                    + "FROM DocumentMetadata dm "
                    + "WHERE dm.documentGroup = :documentGroup "
                    + "AND dm.majorVersion = :majorVersion "
                    + "AND dm.minorVersion = :minorVersion "
                    + "AND dm.patchVersion = :patchVersion")
    boolean existsVersion(
            @Param("documentGroup") DocumentGroup documentGroup,
            @Param("majorVersion") int majorVersion,
            @Param("minorVersion") int minorVersion,
            @Param("patchVersion") int patchVersion);

    boolean existsByDocumentGroupProjectIdAndHash(UUID projectId, String hash);

    @Query(
            "SELECT dm.id FROM DocumentMetadata dm "
                    + "WHERE dm.documentGroup.projectId = :projectId "
                    + "AND dm.documentGroup.groupName = :groupName")
    List<Long> findIdsByProjectIdAndGroupName(
            @Param("projectId") UUID projectId, @Param("groupName") String groupName);

    @Query(
            "SELECT dm.id FROM DocumentMetadata dm "
                    + "WHERE dm.documentGroup.projectId = :projectId "
                    + "AND dm.documentGroup.category = :category")
    List<Long> findIdsByProjectIdAndCategory(
            @Param("projectId") UUID projectId, @Param("category") String category);
}

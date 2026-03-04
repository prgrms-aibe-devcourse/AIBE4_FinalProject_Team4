package kr.java.documind.domain.archive.document.model.repository;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {

    boolean existsByDocumentGroupAndMajorVersionAndMinorVersionAndPatchVersion(
            DocumentGroup documentGroup, int majorVersion, int minorVersion, int patchVersion);

    long countByDocumentGroup(DocumentGroup documentGroup);

    List<DocumentMetadata>
            findByDocumentGroupOrderByMajorVersionDescMinorVersionDescPatchVersionDesc(
                    DocumentGroup documentGroup);

    @Query(
            "SELECT CASE WHEN COUNT(dm) > 0 THEN true ELSE false END "
                    + "FROM DocumentMetadata dm "
                    + "WHERE dm.documentGroup.projectId = :projectId AND dm.hash = :hash")
    boolean existsByProjectIdAndHash(
            @Param("projectId") UUID projectId, @Param("hash") String hash);
}

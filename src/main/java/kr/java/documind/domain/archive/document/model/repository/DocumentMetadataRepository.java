package kr.java.documind.domain.archive.document.model.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.archive.document.model.entity.DocumentGroup;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import org.springframework.data.domain.Pageable;
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

    /**
     * 동일 문서 그룹 내에서 지정 문서보다 이전 버전을 최신순으로 조회한다.
     *
     * <p>diff 계산 시 직전 버전 탐색에 사용하므로 {@code Pageable}로 1건만 요청한다.
     *
     * @param groupId   document_group.id
     * @param excludeId 현재 버전의 document_metadata.id (자기 자신 제외)
     * @param pageable  {@code PageRequest.of(0, 1)} 로 직전 버전 1건만 조회
     * @return 버전 내림차순 정렬된 이전 버전 목록 (최대 1건)
     */
    @Query(
            "SELECT dm FROM DocumentMetadata dm "
                    + "WHERE dm.documentGroup.id = :groupId "
                    + "AND dm.id <> :excludeId "
                    + "ORDER BY dm.majorVersion DESC, dm.minorVersion DESC, dm.patchVersion DESC")
    List<DocumentMetadata> findPreviousVersionsInGroup(
            @Param("groupId") Long groupId,
            @Param("excludeId") Long excludeId,
            Pageable pageable);
}

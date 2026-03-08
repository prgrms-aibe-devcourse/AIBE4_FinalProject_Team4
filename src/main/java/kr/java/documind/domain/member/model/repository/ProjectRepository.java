package kr.java.documind.domain.member.model.repository;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.member.model.dto.ProjectMemberProjection;
import kr.java.documind.domain.member.model.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByPublicId(String publicId);

    @Query(
            """
            SELECT p.id           AS projectId,
                   p.publicId     AS publicId,
                   p.name         AS projectName,
                   p.status       AS projectStatus,
                   pm.id          AS projectMemberId,
                   pm.projectRole AS projectRole,
                   pm.status      AS memberStatus
            FROM Project p
            LEFT JOIN ProjectMember pm ON pm.project = p AND pm.member.id = :memberId
            WHERE p.publicId = :publicId
            """)
    Optional<ProjectMemberProjection> findProjectWithMemberContext(
            @Param("publicId") String publicId, @Param("memberId") UUID memberId);
}

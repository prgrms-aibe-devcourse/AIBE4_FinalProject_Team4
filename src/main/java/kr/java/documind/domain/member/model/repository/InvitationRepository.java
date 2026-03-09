package kr.java.documind.domain.member.model.repository;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.member.model.entity.Invitation;
import kr.java.documind.domain.member.model.entity.Project;
import kr.java.documind.domain.member.model.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    List<Invitation> findAllByProjectAndTargetEmailIgnoreCaseAndStatus(
            Project project, String targetEmail, InvitationStatus status);

    @Query(
            """
            SELECT COUNT(pm) > 0 FROM ProjectMember pm
            JOIN pm.member m
            WHERE pm.project = :project
              AND LOWER(m.email) = LOWER(:email)
              AND pm.status = 'ACTIVE'
            """)
    boolean existsActiveMemberByProjectAndEmail(
            @Param("project") Project project, @Param("email") String email);
}

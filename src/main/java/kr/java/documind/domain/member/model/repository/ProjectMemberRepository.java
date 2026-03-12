package kr.java.documind.domain.member.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.enums.ProjectRole;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.entity.ProjectMember;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectAndMember(Project project, Member member);

    @Query(
            """
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.member
            WHERE pm.project = :project AND pm.status = :status
            ORDER BY pm.createdAt ASC
            """)
    List<ProjectMember> findByProjectAndStatusFetchMember(
            @Param("project") Project project, @Param("status") AccountStatus status);

    @Query(
            """
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.project
            WHERE pm.member = :member AND pm.status = :status
            ORDER BY pm.project.name ASC
            """)
    List<ProjectMember> findByMemberAndStatusFetchProject(
            @Param("member") Member member, @Param("status") AccountStatus status);

    @Query(
            """
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.project
            WHERE pm.member.id = :memberId AND pm.status = :status
            ORDER BY pm.project.name ASC
            """)
    List<ProjectMember> findByMemberIdAndStatusFetchProject(
            @Param("memberId") UUID memberId, @Param("status") AccountStatus status);

    List<ProjectMember> findAllByProjectAndStatusNot(Project project, AccountStatus status);

    long countByProjectAndProjectRoleAndStatus(
            Project project, ProjectRole projectRole, AccountStatus status);
}

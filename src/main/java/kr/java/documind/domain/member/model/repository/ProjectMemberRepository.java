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

    /**
     * 프로젝트 ID와 역할로 활성 멤버를 조회한다.
     *
     * <p>프로젝트 관리자 조회 시 사용 (이슈 기본 담당자 할당용)
     */
    @Query(
            """
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.member
            WHERE pm.project.id = :projectId
              AND pm.projectRole = :projectRole
              AND pm.status = 'ACTIVE'
            """)
    List<ProjectMember> findByProject_IdAndProjectRole(
            @Param("projectId") UUID projectId, @Param("projectRole") ProjectRole projectRole);

    long countByProjectAndProjectRoleAndStatus(
            Project project, ProjectRole projectRole, AccountStatus status);

    /**
     * 프로젝트 활성 멤버 존재 여부 확인
     *
     * @param projectId 프로젝트 ID
     * @param memberId 멤버 ID
     * @param status 계정 상태
     * @return 존재 여부
     */
    boolean existsByProject_IdAndMember_IdAndStatus(
            UUID projectId, UUID memberId, AccountStatus status);
}

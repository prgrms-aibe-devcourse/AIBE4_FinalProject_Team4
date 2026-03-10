package kr.java.documind.domain.member.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.member.model.entity.ProjectMember;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    /** 특정 프로젝트에서 회원의 멤버십을 조회한다. */
    Optional<ProjectMember> findByProjectAndMember(Project project, Member member);

    /** 프로젝트에 속한 활성 멤버를 회원 정보와 함께 JOIN FETCH 조회한다. (LazyInitializationException 방지) */
    @Query(
            """
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.member
            WHERE pm.project = :project AND pm.status = :status
            ORDER BY pm.createdAt ASC
            """)
    List<ProjectMember> findByProjectAndStatusFetchMember(
            @Param("project") Project project, @Param("status") AccountStatus status);

    /** 회원이 참여 중인 활성 멤버십을 프로젝트 정보와 함께 JOIN FETCH 조회한다. (사이드바 프로젝트 드롭다운용) */
    @Query(
            """
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.project
            WHERE pm.member = :member AND pm.status = :status
            ORDER BY pm.project.name ASC
            """)
    List<ProjectMember> findByMemberAndStatusFetchProject(
            @Param("member") Member member, @Param("status") AccountStatus status);

    /**
     * 회원 ID로 참여 중인 활성 멤버십을 프로젝트 정보와 함께 JOIN FETCH 조회한다.
     *
     * <p>{@link #findByMemberAndStatusFetchProject}의 UUID 오버로드. Member 엔티티 로드 없이 조회하여 불필요한 DB 조회를
     * 제거한다.
     */
    @Query(
            """
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.project
            WHERE pm.member.id = :memberId AND pm.status = :status
            ORDER BY pm.project.name ASC
            """)
    List<ProjectMember> findByMemberIdAndStatusFetchProject(
            @Param("memberId") UUID memberId, @Param("status") AccountStatus status);

    /** 프로젝트에 속한 삭제되지 않은(ACTIVE/SUSPENDED) 모든 멤버를 조회한다. (프로젝트 삭제 시 일괄 소프트 딜리트용) */
    List<ProjectMember> findAllByProjectAndStatusNot(Project project, AccountStatus status);
}

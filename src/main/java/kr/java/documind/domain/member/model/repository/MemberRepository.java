package kr.java.documind.domain.member.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.auth.model.enums.OAuthProvider;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    Optional<Member> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.company WHERE m.id = :id")
    Optional<Member> findWithCompanyById(UUID id);

    @Query(
            "SELECT m FROM Member m "
                    + "WHERE m.email = :email "
                    + "AND m.provider <> :provider "
                    + "AND m.accountStatus <> :deleted")
    Optional<Member> findActiveByEmailAndDifferentProvider(
            @Param("email") String email,
            @Param("provider") OAuthProvider provider,
            @Param("deleted") AccountStatus deleted);

    @Query(
            "SELECT m FROM Member m "
                    + "WHERE m.company.id = :companyId "
                    + "AND m.globalRole = :role")
    Optional<Member> findFirstByCompanyIdAndGlobalRole(
            @Param("companyId") Long companyId, @Param("role") GlobalRole role);

    @Query(
            "SELECT m FROM Member m "
                    + "WHERE m.company.id IN :companyIds "
                    + "AND m.globalRole = :role")
    List<Member> findByCompanyIdInAndGlobalRole(
            @Param("companyIds") List<Long> companyIds, @Param("role") GlobalRole role);

    /**
     * 프로젝트 멤버 중 닉네임으로 검색 (멘션 자동완성용)
     *
     * @param projectId 프로젝트 ID
     * @param nickname 검색할 닉네임 (부분 일치)
     * @param status 멤버 상태
     * @param pageable 페이징 정보 (limit 제어용)
     * @return 매칭된 멤버 목록
     */
    @Query(
            """
            SELECT m FROM Member m
            JOIN ProjectMember pm ON pm.member.id = m.id
            WHERE pm.project.id = :projectId
              AND pm.status = :status
              AND m.nickname LIKE CONCAT('%', :nickname, '%')
            ORDER BY m.nickname ASC
            """)
    List<Member> searchProjectMembersByNickname(
            @Param("projectId") UUID projectId,
            @Param("nickname") String nickname,
            @Param("status") AccountStatus status,
            Pageable pageable);
}

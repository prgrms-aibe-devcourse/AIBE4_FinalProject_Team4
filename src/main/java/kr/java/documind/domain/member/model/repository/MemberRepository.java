package kr.java.documind.domain.member.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.enums.GlobalRole;
import kr.java.documind.domain.member.model.enums.OAuthProvider;
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

    /** 특정 회사의 CEO 멤버를 조회한다. ADMIN 회사 관리 페이지 카드 렌더링에서 사용한다. */
    @Query(
            "SELECT m FROM Member m "
                    + "WHERE m.company.id = :companyId "
                    + "AND m.globalRole = :role")
    Optional<Member> findFirstByCompanyIdAndGlobalRole(
            @Param("companyId") Long companyId, @Param("role") GlobalRole role);

    /**
     * 여러 회사의 CEO를 한 번의 쿼리로 일괄 조회한다. ADMIN 회사 관리 페이지 N+1 방지용.
     *
     * <p>결과를 Map으로 변환하려면 {@code stream().collect(groupingBy(...))} 를 사용한다.
     */
    @Query(
            "SELECT m FROM Member m "
                    + "WHERE m.company.id IN :companyIds "
                    + "AND m.globalRole = :role")
    List<Member> findByCompanyIdInAndGlobalRole(
            @Param("companyIds") List<Long> companyIds, @Param("role") GlobalRole role);
}

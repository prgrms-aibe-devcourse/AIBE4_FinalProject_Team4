package kr.java.documind.domain.member.model.repository;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    Optional<Member> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.company WHERE m.id = :id")
    Optional<Member> findWithCompanyById(UUID id);

    /** 동일 이메일로 다른 provider에 가입된 활성 계정 조회 */
    @Query(
            "SELECT m FROM Member m "
                    + "WHERE m.email = :email "
                    + "AND m.provider <> :provider "
                    + "AND m.accountStatus <> :deleted")
    Optional<Member> findActiveByEmailAndDifferentProvider(
            @Param("email") String email,
            @Param("provider") OAuthProvider provider,
            @Param("deleted") AccountStatus deleted);
}

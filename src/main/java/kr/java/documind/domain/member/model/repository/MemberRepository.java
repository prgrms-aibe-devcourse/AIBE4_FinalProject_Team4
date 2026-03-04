package kr.java.documind.domain.member.model.repository;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    Optional<Member> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.company WHERE m.id = :id")
    Optional<Member> findWithCompanyById(UUID id);
}

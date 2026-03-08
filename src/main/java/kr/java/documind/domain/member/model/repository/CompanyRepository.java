package kr.java.documind.domain.member.model.repository;

import java.util.List;
import java.util.Optional;
import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.enums.CompanyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    /** 삭제되지 않은 회사를 상태별로 신청일 내림차순으로 조회한다. */
    List<Company> findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(CompanyStatus status);

    /** 삭제되지 않은 회사의 상태별 개수를 반환한다. */
    long countByStatusAndDeletedAtIsNull(CompanyStatus status);

    /** 삭제되지 않은 회사를 ID로 단건 조회한다. */
    Optional<Company> findByIdAndDeletedAtIsNull(Long id);
}

package kr.java.documind.domain.member.model.repository;

import java.util.List;
import java.util.Optional;
import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.enums.CompanyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(CompanyStatus status);

    Optional<Company> findByIdAndDeletedAtIsNull(Long id);
}

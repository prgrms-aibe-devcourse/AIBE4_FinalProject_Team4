package kr.java.documind.domain.member.model.repository;

import kr.java.documind.domain.member.model.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {}

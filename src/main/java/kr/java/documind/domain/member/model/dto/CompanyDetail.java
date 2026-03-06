package kr.java.documind.domain.member.model.dto;

import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.enums.CompanyStatus;

public record CompanyDetail(String name, boolean pending, String profileUrl) {

    public static CompanyDetail from(Company company, String profileUrl) {
        return new CompanyDetail(
                company.getName(), company.getStatus() == CompanyStatus.PENDING, profileUrl);
    }
}

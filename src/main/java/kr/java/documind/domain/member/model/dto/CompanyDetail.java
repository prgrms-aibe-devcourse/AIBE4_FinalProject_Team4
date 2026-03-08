package kr.java.documind.domain.member.model.dto;

import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.enums.CompanyStatus;

public record CompanyDetail(String name, CompanyStatus status, String profileUrl) {

    /** 승인 대기 중 여부 */
    public boolean pending() {
        return status == CompanyStatus.PENDING;
    }

    /** 승인 완료 여부 */
    public boolean approved() {
        return status == CompanyStatus.APPROVED;
    }

    /** 거부(정지) 여부 */
    public boolean suspended() {
        return status == CompanyStatus.SUSPENDED;
    }

    public static CompanyDetail from(Company company, String profileUrl) {
        return new CompanyDetail(company.getName(), company.getStatus(), profileUrl);
    }
}

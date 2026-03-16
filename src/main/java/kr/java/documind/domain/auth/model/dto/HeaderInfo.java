package kr.java.documind.domain.auth.model.dto;

import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.CompanyStatus;

public record HeaderInfo(
        String name,
        String nickname,
        GlobalRole globalRole,
        String profileImageUrl,
        String companyName,
        String companyProfileUrl,
        CompanyStatus companyStatus,
        String roleLabel,
        boolean showFreePlanBadge,
        String profileFallbackLetter) {

    public static HeaderInfo from(Member member, String profileImageUrl, String companyProfileUrl) {
        String resolvedName =
                member.getName() != null && !member.getName().isBlank() ? member.getName() : "사용자";
        String resolvedNickname = member.getNickname();
        Company company = member.getCompany();
        String resolvedCompanyName = company != null ? company.getName() : null;
        CompanyStatus companyStatus = company != null ? company.getStatus() : null;
        GlobalRole role = member.getGlobalRole();
        String resolvedRoleLabel =
                switch (role) {
                    case CEO -> "대표";
                    case ADMIN -> "운영자";
                    case EMPLOYEE -> "직원";
                };
        boolean resolvedShowFreePlanBadge = role != GlobalRole.ADMIN;
        String fallbackLetter =
                (resolvedNickname != null && !resolvedNickname.isBlank())
                        ? resolvedNickname.substring(0, 1)
                        : "U";

        return new HeaderInfo(
                resolvedName,
                resolvedNickname,
                role,
                profileImageUrl,
                resolvedCompanyName,
                companyProfileUrl,
                companyStatus,
                resolvedRoleLabel,
                resolvedShowFreePlanBadge,
                fallbackLetter);
    }
}

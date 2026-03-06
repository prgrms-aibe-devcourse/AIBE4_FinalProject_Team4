package kr.java.documind.domain.member.model.dto;

import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.GlobalRole;

public record HeaderInfo(
        String name,
        String nickname,
        GlobalRole globalRole,
        String profileImageUrl,
        String companyName,
        String companyProfileUrl) {

    public static HeaderInfo from(Member member, String profileImageUrl, String companyProfileUrl) {
        Company company = member.getCompany();
        return new HeaderInfo(
                member.getName(),
                member.getNickname(),
                member.getGlobalRole(),
                profileImageUrl,
                company != null ? company.getName() : null,
                companyProfileUrl);
    }
}

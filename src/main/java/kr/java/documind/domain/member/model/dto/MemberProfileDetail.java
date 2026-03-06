package kr.java.documind.domain.member.model.dto;

import java.time.format.DateTimeFormatter;
import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.CompanyStatus;
import kr.java.documind.domain.member.model.enums.GlobalRole;

public record MemberProfileDetail(
        String name,
        String nickname,
        String email,
        GlobalRole globalRole,
        String position,
        String profileImageUrl,
        String companyName,
        boolean companyPending,
        String joinedAt) {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public static MemberProfileDetail from(Member member, String profileImageUrl) {
        Company company = member.getCompany();
        boolean isAdmin = member.getGlobalRole() == GlobalRole.ADMIN;

        String formattedDate =
                member.getCreatedAt() != null ? member.getCreatedAt().format(DATE_FMT) : null;

        return new MemberProfileDetail(
                member.getName(),
                member.getNickname(),
                member.getEmail(),
                member.getGlobalRole(),
                isAdmin ? null : member.getPosition(),
                profileImageUrl,
                isAdmin || company == null ? null : company.getName(),
                company != null && company.getStatus() == CompanyStatus.PENDING,
                formattedDate);
    }
}

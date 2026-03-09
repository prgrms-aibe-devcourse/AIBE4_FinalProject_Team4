package kr.java.documind.domain.member.model.dto;

import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.enums.CompanyStatus;

public record AdminCompanyCard(
        Long companyId,
        String companyName,
        String companyProfileUrl,
        CompanyStatus companyStatus,
        /** company.createdAt → "yyyy.MM.dd" 포맷 */
        String appliedAt,
        /** company.updatedAt → "yyyy.MM.dd" 포맷. APPROVED 탭에서는 승인일로 표시 */
        String updatedAt,
        // ── CEO 정보 (ceoMissing=true 이면 아래 필드 전부 null) ──
        String ceoName,
        String ceoEmail,
        AccountStatus ceoAccountStatus,
        String ceoPosition,
        String ceoProfileUrl,
        /** CEO가 존재하지 않거나 DELETED 상태인 경우 true */
        boolean ceoMissing) {}

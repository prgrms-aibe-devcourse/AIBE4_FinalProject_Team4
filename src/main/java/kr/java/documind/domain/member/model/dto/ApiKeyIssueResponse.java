package kr.java.documind.domain.member.model.dto;

public record ApiKeyIssueResponse(
        /** 평문 API 키 — 1회만 노출 */
        String plainKey,
        /** 마스킹된 표시용 키 (prefix 앞 12자 + **** + last4) */
        String maskedKey) {}

package kr.java.documind.domain.member.model.dto;

import kr.java.documind.domain.member.model.enums.ApiKeyStatus;

public record ProjectApiKeyInfo(boolean hasKey, String maskedKey, ApiKeyStatus status) {

    public boolean active() {
        return status == ApiKeyStatus.ACTIVE;
    }

    public boolean suspended() {
        return status == ApiKeyStatus.SUSPENDED;
    }
}

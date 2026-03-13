package kr.java.documind.domain.member.model.dto;

import kr.java.documind.domain.member.model.enums.ApiKeyStatus;
import kr.java.documind.domain.auth.model.enums.ApiKeyType;

public record ProjectApiKeyInfo(KeyMetadata ingestKey, KeyMetadata queryKey) {
    public record KeyMetadata(
            boolean hasKey, ApiKeyType keyType, String maskedKey, ApiKeyStatus status) {
        public boolean active() {
            return status == ApiKeyStatus.ACTIVE;
        }

        public boolean suspended() {
            return status == ApiKeyStatus.SUSPENDED;
        }
    }
}

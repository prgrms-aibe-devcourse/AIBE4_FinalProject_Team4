package kr.java.documind.domain.auth.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import kr.java.documind.domain.auth.model.enums.ApiKeyType;
import kr.java.documind.domain.member.model.enums.ApiKeyStatus;
import kr.java.documind.global.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_api_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectApiKey extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 20)
    private ApiKeyType keyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "api_key_status", nullable = false, length = 20)
    private ApiKeyStatus apiKeyStatus;

    @Column(name = "key_prefix", nullable = false, length = 32)
    private String keyPrefix;

    @Column(name = "key_last4", nullable = false, length = 4)
    private String keyLast4;

    @Column(name = "revoked_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime revokedAt;

    public static ProjectApiKey create(
            Project project,
            String apiKeyHash,
            String keyPrefix,
            String keyLast4,
            ApiKeyType keyType) {
        ProjectApiKey apiKey = new ProjectApiKey();
        apiKey.project = project;
        apiKey.apiKeyHash = apiKeyHash;
        apiKey.keyPrefix = keyPrefix;
        apiKey.keyLast4 = keyLast4;
        apiKey.keyType = keyType;
        apiKey.apiKeyStatus = ApiKeyStatus.ACTIVE;
        return apiKey;
    }

    public void suspend() {
        this.apiKeyStatus = ApiKeyStatus.SUSPENDED;
    }

    public void revoke() {
        this.apiKeyStatus = ApiKeyStatus.REVOKED;
        this.revokedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void activate() {
        this.apiKeyStatus = ApiKeyStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.apiKeyStatus == ApiKeyStatus.ACTIVE;
    }
}

package kr.java.documind.domain.member.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.enums.GlobalRole;
import kr.java.documind.domain.member.model.enums.OAuthProvider;
import kr.java.documind.global.entity.UuidBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "member",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_member_email", columnNames = "email"),
            @UniqueConstraint(
                    name = "uk_member_provider",
                    columnNames = {"provider", "provider_id"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends UuidBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "global_role", nullable = false, length = 20)
    private GlobalRole globalRole;

    @Column(nullable = false, length = 20)
    private String position;

    @Column(name = "profile_key")
    private String profileKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Member createByOAuth(
            String email,
            String name,
            String nickname,
            OAuthProvider provider,
            String providerId,
            GlobalRole globalRole) {
        Member member = new Member();
        member.email = email;
        member.name = name;
        member.nickname = nickname;
        member.globalRole = globalRole;
        member.position = "JUNIOR";
        member.provider = provider;
        member.providerId = providerId;
        member.accountStatus = AccountStatus.ACTIVE;
        return member;
    }

    public void updateProfile(String nickname, String profileKey, String position) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (profileKey != null) {
            this.profileKey = profileKey;
        }
        if (position != null) {
            this.position = position;
        }
    }

    public void assignCompany(Company company) {
        this.company = company;
    }

    public void suspend() {
        this.accountStatus = AccountStatus.SUSPENDED;
    }

    public void activate() {
        this.accountStatus = AccountStatus.ACTIVE;
    }

    public void softDelete() {
        this.accountStatus = AccountStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.accountStatus == AccountStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return this.globalRole == GlobalRole.ADMIN;
    }

    public boolean isCeo() {
        return this.globalRole == GlobalRole.CEO;
    }

    public boolean isEmployee() {
        return this.globalRole == GlobalRole.EMPLOYEE;
    }

    public boolean isEmailPlaceholder() {
        return this.email != null && this.email.endsWith("@oauth.placeholder");
    }
}

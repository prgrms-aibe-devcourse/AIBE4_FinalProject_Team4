package kr.java.documind.domain.member.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import kr.java.documind.domain.member.model.enums.InvitationStatus;
import kr.java.documind.domain.member.model.enums.ProjectRole;
import kr.java.documind.global.entity.UuidBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation extends UuidBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_id", nullable = false)
    private Member inviter;

    @Column(name = "target_email", nullable = false, length = 150)
    private String targetEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = false, length = 20)
    private ProjectRole targetRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static Invitation create(
            Project project,
            Member member,
            Member inviter,
            String targetEmail,
            ProjectRole targetRole,
            LocalDateTime expiresAt) {
        Invitation invitation = new Invitation();
        invitation.project = project;
        invitation.member = member;
        invitation.inviter = inviter;
        invitation.targetEmail = targetEmail;
        invitation.targetRole = targetRole;
        invitation.status = InvitationStatus.PENDING;
        invitation.expiresAt = expiresAt;
        return invitation;
    }

    public void use() {
        this.status = InvitationStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    public void revoke() {
        this.status = InvitationStatus.REVOKED;
        this.revokedAt = LocalDateTime.now();
    }

    public void expire() {
        this.status = InvitationStatus.EXPIRED;
    }

    public boolean isPending() {
        return this.status == InvitationStatus.PENDING;
    }

    public boolean isExpired() {
        return this.status == InvitationStatus.EXPIRED
                || LocalDateTime.now().isAfter(this.expiresAt);
    }
}

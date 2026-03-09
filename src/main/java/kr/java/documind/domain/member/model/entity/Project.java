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
import kr.java.documind.domain.member.model.enums.ProjectStatus;
import kr.java.documind.global.entity.UuidBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends UuidBaseEntity {

    @Column(name = "public_id", nullable = false, length = 20, unique = true, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "profile_key")
    private String profileKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Project create(String publicId, Company company, String name, String profileKey) {
        Project project = new Project();
        project.publicId = publicId;
        project.company = company;
        project.name = name;
        project.profileKey = profileKey;
        project.status = ProjectStatus.ACTIVE;
        return project;
    }

    public void updateInfo(String name, String profileKey) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (profileKey != null) {
            this.profileKey = profileKey;
        }
    }

    public void suspend() {
        this.status = ProjectStatus.SUSPENDED;
    }

    public void activate() {
        this.status = ProjectStatus.ACTIVE;
    }

    public void softDelete() {
        this.status = ProjectStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == ProjectStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return this.status == ProjectStatus.DELETED;
    }
}

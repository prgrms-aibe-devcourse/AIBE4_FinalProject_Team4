package kr.java.documind.domain.member.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import kr.java.documind.domain.member.model.enums.CompanyStatus;
import kr.java.documind.global.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "company")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "profile_key")
    private String profileKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyStatus status;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime deletedAt;

    public static Company create(String name) {
        Company company = new Company();
        company.name = name;
        company.status = CompanyStatus.PENDING;
        return company;
    }

    public void updateName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public void updateProfileKey(String profileKey) {
        if (profileKey != null) {
            this.profileKey = profileKey;
        }
    }

    /** 운영자가 회사 가입 신청을 승인한다. updatedAt 은 @LastModifiedDate 로 자동 갱신된다. */
    public void approve() {
        this.status = CompanyStatus.APPROVED;
    }

    /** 운영자가 회사 가입 신청을 거부(정지)한다. updatedAt 은 @LastModifiedDate 로 자동 갱신된다. */
    public void reject() {
        this.status = CompanyStatus.SUSPENDED;
    }
}

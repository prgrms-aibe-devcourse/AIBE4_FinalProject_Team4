package kr.java.documind.domain.member.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import kr.java.documind.global.entity.UuidBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends UuidBaseEntity {

    @Column(nullable = false, unique = true)
    private String publicId;

    private Project(String publicId) {
        this.publicId = publicId;
    }

    public static Project create(String publicId) {
        return new Project(publicId);
    }
}

package kr.java.documind.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import kr.java.documind.global.enums.SourceType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomainSource extends BaseEntity {

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    private DomainSource(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public static DomainSource create(SourceType sourceType) {
        return new DomainSource(sourceType);
    }
}

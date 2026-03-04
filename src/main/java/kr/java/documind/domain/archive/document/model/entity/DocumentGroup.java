package kr.java.documind.domain.archive.document.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import kr.java.documind.global.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"project_id", "category", "group_name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentGroup extends BaseEntity {

    // TODO: Project 엔티티 생성 후 @ManyToOne(fetch = LAZY) + @JoinColumn으로 전환
    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 10)
    private String category;

    @Column(nullable = false, length = 30)
    private String groupName;

    @Column(nullable = false)
    private String choseong;

    private DocumentGroup(UUID projectId, String category, String groupName, String choseong) {
        this.projectId = projectId;
        this.category = category;
        this.groupName = groupName;
        this.choseong = choseong;
    }

    public static DocumentGroup create(
            UUID projectId, String category, String groupName, String choseong) {
        return new DocumentGroup(projectId, category, groupName, choseong);
    }

    public void updateCategory(String category) {
        this.category = category;
    }

    public void updateGroupName(String groupName, String choseong) {
        this.groupName = groupName;
        this.choseong = choseong;
    }
}

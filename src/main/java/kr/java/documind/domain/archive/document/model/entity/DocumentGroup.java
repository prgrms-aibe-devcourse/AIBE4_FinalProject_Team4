package kr.java.documind.domain.archive.document.model.entity;

import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import kr.java.documind.domain.member.model.entity.Project;
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

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 10)
    private String category;

    @Column(nullable = false, length = 30)
    private String groupName;

    @Column(nullable = false)
    private String choseong;

    private DocumentGroup(Project project, String category, String groupName, String choseong) {
        this.project = project;
        this.category = category;
        this.groupName = groupName;
        this.choseong = choseong;
    }

    public static DocumentGroup create(
            Project project, String category, String groupName, String choseong) {
        return new DocumentGroup(project, category, groupName, choseong);
    }

    public UUID getProjectId() {
        return project.getId();
    }

    public void updateCategory(String category) {
        this.category = category;
    }

    public void updateGroupName(String groupName, String choseong) {
        this.groupName = groupName;
        this.choseong = choseong;
    }
}

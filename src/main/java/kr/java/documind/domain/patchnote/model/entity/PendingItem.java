package kr.java.documind.domain.patchnote.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;

import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.global.entity.BaseEntity;
import kr.java.documind.global.enums.SourceType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "pending_item",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_pending_item_project_source",
            columnNames = {"project_id", "source_type", "source_id"})
    },
    indexes = {
        @Index(name = "idx_pending_item_project_status", columnList = "project_id, status"),
        @Index(name = "idx_pending_item_project_source_created_at", columnList = "project_id, source_created_at"),
        @Index(name = "idx_pending_item_project_patch_type", columnList = "project_id, patch_type")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingItem extends BaseEntity {

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private SourceType sourceType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(nullable = false, length = 255)
    private String choseong;

    @Enumerated(EnumType.STRING)
    @Column(name = "patch_type", nullable = false, length = 50)
    private PatchType patchType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PendingItemStatus status;

    @Column(name = "is_source_deleted", nullable = false)
    private boolean sourceDeleted;

    @Column(name = "source_created_at", nullable = false)
    private LocalDateTime sourceCreatedAt;

    public static PendingItem create(
        UUID projectId,
        Long sourceId,
        SourceType sourceType,
        String title,
        String summary,
        String choseong,
        PatchType patchType,
        PendingItemStatus status,
        LocalDateTime sourceCreatedAt) {
        PendingItem pendingItem = new PendingItem();
        pendingItem.projectId = projectId;
        pendingItem.sourceId = sourceId;
        pendingItem.sourceType = sourceType;
        pendingItem.title = title;
        pendingItem.summary = summary;
        pendingItem.choseong = choseong;
        pendingItem.patchType = patchType;
        pendingItem.status = status;
        pendingItem.sourceDeleted = false;
        pendingItem.sourceCreatedAt = sourceCreatedAt;
        return pendingItem;
    }

    public void refresh(
        String title,
        String summary,
        String choseong,
        PatchType patchType,
        PendingItemStatus status,
        LocalDateTime sourceCreatedAt) {
        this.title = title;
        this.summary = summary;
        this.choseong = choseong;
        this.patchType = patchType;
        this.status = status;
        this.sourceCreatedAt = sourceCreatedAt;
        this.sourceDeleted = false;
    }

    public void exclude() {
        this.status = PendingItemStatus.EXCLUDED;
    }

    public void restore() {
        this.status = PendingItemStatus.PENDING;
    }

    public void complete() {
        this.status = PendingItemStatus.COMPLETED;
    }

    public void markSourceDeleted() {
        this.sourceDeleted = true;
    }

    public boolean isExcluded() {
        return this.status == PendingItemStatus.EXCLUDED;
    }

    public boolean isPending() {
        return this.status == PendingItemStatus.PENDING;
    }

    public boolean isCompleted() {
        return this.status == PendingItemStatus.COMPLETED;
    }
}

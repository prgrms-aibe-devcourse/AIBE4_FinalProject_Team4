package kr.java.documind.domain.patchnote.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
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
            @Index(
                    name = "idx_pending_item_project_source_created_at",
                    columnList = "project_id, source_created_at"),
            @Index(
                    name = "idx_pending_item_project_patch_type",
                    columnList = "project_id, patch_type")
            // 아래 인덱스는 JPA 미지원 → V11 마이그레이션에서 관리
            // - idx_pending_item_title_bigm     (GIN, pg_bigm)
            // - idx_pending_item_summary_bigm   (GIN, pg_bigm)
            // - idx_pending_item_choseong       (B-tree)
            // - idx_pending_item_source_deleted (partial, WHERE is_source_deleted = TRUE)
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

    @Column(name = "source_created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime sourceCreatedAt;

    public static PendingItem create(
            UUID projectId,
            Long sourceId,
            SourceType sourceType,
            String title,
            String summary,
            String choseong,
            PatchType patchType,
            PendingItemStatus status,
            OffsetDateTime sourceCreatedAt) {
        PendingItem pendingItem = new PendingItem();
        pendingItem.projectId = projectId;
        pendingItem.sourceId = sourceId;
        pendingItem.sourceType = sourceType;
        pendingItem.title = title;
        pendingItem.summary = summary;
        pendingItem.choseong = choseong != null ? choseong : "";
        pendingItem.patchType = patchType;
        pendingItem.status = status;
        pendingItem.sourceDeleted = false;
        pendingItem.sourceCreatedAt = sourceCreatedAt;
        return pendingItem;
    }

    // status, sourceDeleted는 갱신하지 않음
    // 사용자가 수동으로 EXCLUDED 처리한 항목을 이벤트 재발행이 덮어쓰는 것을 방지
    public void refresh(
            String title,
            String summary,
            String choseong,
            PatchType patchType,
            OffsetDateTime sourceCreatedAt) {
        this.title = title;
        this.summary = summary;
        this.choseong = choseong != null ? choseong : "";
        this.patchType = patchType;
        this.sourceCreatedAt = sourceCreatedAt;
    }

    public void exclude() {
        this.status = PendingItemStatus.EXCLUDED;
    }

    public void restore() {
        if (this.status != PendingItemStatus.EXCLUDED) {
            throw new IllegalStateException("EXCLUDED 상태에서만 복원할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PendingItemStatus.PENDING;
    }

    public void complete() {
        if (this.status != PendingItemStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 완료 처리할 수 있습니다. 현재 상태: " + this.status);
        }
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

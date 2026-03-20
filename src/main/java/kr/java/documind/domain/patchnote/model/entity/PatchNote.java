package kr.java.documind.domain.patchnote.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.enums.PatchNoteStatus;
import kr.java.documind.global.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "patch_note",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_patch_note_project_version",
                    columnNames = {"project_id", "major_version", "minor_version", "patch_version"})
        },
        indexes = {
            @Index(name = "idx_patch_note_project_status", columnList = "project_id, status"),
            @Index(
                    name = "idx_patch_note_project_created_at",
                    columnList = "project_id, created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatchNote extends BaseEntity {

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PatchNoteStatus status;

    @Column(name = "major_version", nullable = false)
    private Integer majorVersion;

    @Column(name = "minor_version", nullable = false)
    private Integer minorVersion;

    @Column(name = "patch_version", nullable = false)
    private Integer patchVersion;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime deletedAt;

    public static PatchNote createDraft(
            UUID projectId,
            String title,
            String content,
            Integer majorVersion,
            Integer minorVersion,
            Integer patchVersion) {

        if (majorVersion < 0 || minorVersion < 0 || patchVersion < 0) {
            throw new IllegalArgumentException("버전은 0 이상이어야 합니다.");
        }

        PatchNote patchNote = new PatchNote();
        patchNote.projectId = projectId;
        patchNote.title = title;
        patchNote.content = content;
        patchNote.status = PatchNoteStatus.DRAFT;
        patchNote.majorVersion = majorVersion;
        patchNote.minorVersion = minorVersion;
        patchNote.patchVersion = patchVersion;
        return patchNote;
    }

    // ── 미사용 메서드 (TODO 미구현 기능) ────────────────────────────────────
    // 아래 두 메서드는 현재 대응 API가 없어 호출되지 않는다.
    // 유저 시나리오상 PatchNoteStatus는 DRAFT / DELETED만 사용한다.
    // 향후 편집·발행 기능 추가 시 주석을 해제하고 API 엔드포인트를 함께 구현한다.

    // public void updateContent(String title, String content) {
    //     if (this.status == PatchNoteStatus.DELETED) {
    //         throw new IllegalStateException("삭제된 패치노트는 수정할 수 없습니다.");
    //     }
    //     this.title = title;
    //     this.content = content;
    // }

    // public void publish() {
    //     if (this.status != PatchNoteStatus.DRAFT) {
    //         throw new IllegalStateException("DRAFT 상태에서만 발행할 수 있습니다. 현재 상태: " + this.status);
    //     }
    //     this.status = PatchNoteStatus.PUBLISHED;
    // }

    public void softDelete() {
        if (this.status == PatchNoteStatus.DELETED) {
            return;
        }
        this.status = PatchNoteStatus.DELETED;
        this.deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public String getVersionLabel() {
        return "v" + majorVersion + "." + minorVersion + "." + patchVersion;
    }

    public boolean isDraft() {
        return this.status == PatchNoteStatus.DRAFT;
    }

    // public boolean isPublished() { return this.status == PatchNoteStatus.PUBLISHED; }

    public boolean isDeleted() {
        return this.status == PatchNoteStatus.DELETED;
    }
}

package kr.java.documind.domain.archive.document.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import kr.java.documind.global.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {
                        "document_group_id",
                        "major_version",
                        "minor_version",
                        "patch_version"
                    })
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentMetadata extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_group_id", nullable = false)
    private DocumentGroup documentGroup;

    @Column(nullable = false)
    private String documentName;

    @Column(nullable = false)
    private String choseong;

    @Column(nullable = false)
    private String extension;

    @Column(nullable = false)
    private int majorVersion;

    @Column(nullable = false)
    private int minorVersion;

    @Column(nullable = false)
    private int patchVersion;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private String storedKey;

    @Column(nullable = false)
    private boolean isProcessed;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    private LocalDateTime reuploadedAt;

    private DocumentMetadata(
            DocumentGroup documentGroup,
            String documentName,
            String choseong,
            String extension,
            int majorVersion,
            int minorVersion,
            int patchVersion,
            String hash,
            long size,
            String storedKey,
            LocalDateTime uploadedAt) {
        this.documentGroup = documentGroup;
        this.documentName = documentName;
        this.choseong = choseong;
        this.extension = extension;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.patchVersion = patchVersion;
        this.hash = hash;
        this.size = size;
        this.storedKey = storedKey;
        this.isProcessed = false;
        this.uploadedAt = uploadedAt;
    }

    public static DocumentMetadata create(
            DocumentGroup documentGroup,
            String documentName,
            String choseong,
            String extension,
            int majorVersion,
            int minorVersion,
            int patchVersion,
            String hash,
            long size,
            String storedKey,
            LocalDateTime uploadedAt) {
        return new DocumentMetadata(
                documentGroup,
                documentName,
                choseong,
                extension,
                majorVersion,
                minorVersion,
                patchVersion,
                hash,
                size,
                storedKey,
                uploadedAt);
    }

    public String getVersionString() {
        return "v" + majorVersion + "." + minorVersion + "." + patchVersion;
    }

    public void updateVersion(int major, int minor, int patch) {
        this.majorVersion = major;
        this.minorVersion = minor;
        this.patchVersion = patch;
    }

    public void updateFile(
            String documentName,
            String choseong,
            String extension,
            String hash,
            long size,
            String storedKey,
            LocalDateTime reuploadedAt) {
        this.documentName = documentName;
        this.choseong = choseong;
        this.extension = extension;
        this.hash = hash;
        this.size = size;
        this.storedKey = storedKey;
        this.reuploadedAt = reuploadedAt;
        this.isProcessed = false;
    }

    public void markAsProcessed() {
        this.isProcessed = true;
    }
}

package kr.java.documind.domain.archive.document.model.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;
import kr.java.documind.global.entity.DomainSource;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
@EntityListeners(AuditingEntityListener.class)
public class DocumentMetadata {

    @Id private Long id;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @MapsId
    @JoinColumn(name = "id")
    private DomainSource domainSource;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmbeddingStatus embeddingStatus;

    @Column(nullable = false, columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime uploadedAt;

    @Column(columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime reuploadedAt;

    @CreatedDate
    @Column(updatable = false, columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime updatedAt;

    private DocumentMetadata(
            DomainSource domainSource,
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
            boolean isProcessed,
            EmbeddingStatus embeddingStatus,
            LocalDateTime uploadedAt) {
        this.domainSource = domainSource;
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
        this.isProcessed = isProcessed;
        this.embeddingStatus = embeddingStatus;
        this.uploadedAt = uploadedAt;
    }

    public static DocumentMetadata create(
            DomainSource domainSource,
            DocumentGroup documentGroup,
            String documentName,
            String extension,
            int majorVersion,
            int minorVersion,
            int patchVersion,
            String hash,
            long size,
            String storedKey,
            boolean isProcessed,
            EmbeddingStatus embeddingStatus,
            OffsetDateTime uploadedAt) {
        return new DocumentMetadata(
                domainSource,
                documentGroup,
                documentName,
                "초성",
                extension,
                majorVersion,
                minorVersion,
                patchVersion,
                hash,
                size,
                storedKey,
                isProcessed,
                embeddingStatus,
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
            String documentName, String extension, String hash, long size, String storedKey) {
        this.documentName = documentName;
        this.choseong = "초성수정";
        this.extension = extension;
        this.hash = hash;
        this.size = size;
        this.storedKey = storedKey;
    }

    public void changeProcessed(boolean isProcessed) {
        this.isProcessed = isProcessed;
    }

    public void changeEmbeddingStatus(EmbeddingStatus embeddingStatus) {
        this.embeddingStatus = embeddingStatus;
    }

    public void markModified() {
        this.reuploadedAt = LocalDateTime.now();
    }
}

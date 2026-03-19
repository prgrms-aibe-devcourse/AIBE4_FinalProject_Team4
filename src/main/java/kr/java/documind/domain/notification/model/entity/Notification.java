package kr.java.documind.domain.notification.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import kr.java.documind.domain.notification.model.enums.NotificationEventType;
import kr.java.documind.global.entity.BaseEntity;
import kr.java.documind.global.entity.DomainSource;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(name = "receiver_id", nullable = false, columnDefinition = "uuid")
    private UUID receiverId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private DomainSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private NotificationEventType eventType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(length = 20)
    private String severity;

    @Column(name = "is_toast", nullable = false)
    private boolean isToast;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "is_ignored", nullable = false)
    private boolean isIgnored;

    @Column(name = "related_url", length = 255)
    private String relatedUrl;

    private Notification(
            UUID projectId,
            UUID receiverId,
            DomainSource source,
            NotificationEventType eventType,
            String title,
            String message,
            String severity,
            boolean isToast,
            String relatedUrl) {
        this.projectId = projectId;
        this.receiverId = receiverId;
        this.source = source;
        this.eventType = eventType;
        this.title = title;
        this.message = message;
        this.severity = severity;
        this.isToast = isToast;
        this.isRead = false;
        this.isIgnored = false;
        this.relatedUrl = relatedUrl;
    }

    public static Notification create(
            UUID projectId,
            UUID receiverId,
            DomainSource source,
            NotificationEventType eventType,
            String title,
            String message,
            String severity,
            boolean isToast,
            String relatedUrl) {
        return new Notification(
                projectId,
                receiverId,
                source,
                eventType,
                title,
                message,
                severity,
                isToast,
                relatedUrl);
    }

    public void markRead() {
        this.isRead = true;
    }

    public void markIgnored() {
        this.isIgnored = true;
    }
}

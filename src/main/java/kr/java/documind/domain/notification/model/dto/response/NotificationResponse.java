package kr.java.documind.domain.notification.model.dto.response;

import java.time.OffsetDateTime;
import kr.java.documind.domain.notification.model.entity.Notification;
import kr.java.documind.global.enums.SourceType;

public record NotificationResponse(
        Long id,
        String eventType,
        String title,
        String message,
        String severity,
        boolean isToast,
        boolean isRead,
        boolean isIgnored,
        String relatedUrl,
        SourceType sourceType,
        String actionText,
        OffsetDateTime createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getEventType().name(),
                n.getTitle(),
                n.getMessage(),
                n.getSeverity(),
                n.isToast(),
                n.isRead(),
                n.isIgnored(),
                n.getRelatedUrl(),
                n.getSource().getSourceType(),
                n.getEventType().getActionText(),
                n.getCreatedAt());
    }
}

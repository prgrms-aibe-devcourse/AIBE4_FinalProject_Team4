package kr.java.documind.domain.notification.event;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.notification.model.enums.NotificationEventType;

public record LogNotificationEvent(
        UUID projectId,
        List<UUID> receiverIds,
        Long sourceId,
        NotificationEventType eventType,
        String title,
        String message,
        String relatedUrl,
        boolean isToast)
        implements NotificationEvent {}

package kr.java.documind.domain.notification.event;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.notification.model.enums.NotificationEventType;

public record IssueNotificationEvent(
        UUID projectId,
        List<UUID> receiverIds,
        Long sourceId,
        NotificationEventType eventType,
        String title,
        String message,
        String relatedUrl,
        boolean isToast,
        IssueSeverity severity)
        implements NotificationEvent {}

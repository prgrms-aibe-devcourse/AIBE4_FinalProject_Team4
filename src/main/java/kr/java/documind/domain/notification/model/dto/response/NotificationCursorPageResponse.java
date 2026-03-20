package kr.java.documind.domain.notification.model.dto.response;

import java.util.List;

public record NotificationCursorPageResponse(
        List<NotificationResponse> notifications, Long nextCursorId, boolean hasNext) {}

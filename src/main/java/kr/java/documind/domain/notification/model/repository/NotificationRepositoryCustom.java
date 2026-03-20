package kr.java.documind.domain.notification.model.repository;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.notification.model.entity.Notification;
import kr.java.documind.global.enums.SourceType;

public interface NotificationRepositoryCustom {

    List<Notification> findCursorPage(
            UUID projectId, UUID receiverId, Long cursorId, SourceType sourceType, int limit);
}

package kr.java.documind.domain.notification.service;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.notification.model.dto.response.NotificationCursorPageResponse;
import kr.java.documind.domain.notification.model.dto.response.NotificationResponse;
import kr.java.documind.domain.notification.model.entity.Notification;
import kr.java.documind.domain.notification.model.repository.NotificationRepository;
import kr.java.documind.global.enums.SourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    public NotificationCursorPageResponse getNotifications(
            UUID projectId, UUID memberId, Long cursorId, SourceType sourceType, int size) {

        List<Notification> raw =
                notificationRepository.findCursorPage(
                        projectId, memberId, cursorId, sourceType, size);

        boolean hasNext = raw.size() > size;
        Long nextCursorId = hasNext ? raw.get(size).getId() : null;
        List<NotificationResponse> items =
                raw.stream().limit(size).map(NotificationResponse::from).toList();

        return new NotificationCursorPageResponse(items, nextCursorId, hasNext);
    }

    public long getUnreadCount(UUID projectId, UUID memberId) {
        return notificationRepository.countByProjectIdAndReceiverIdAndIsReadFalseAndIsIgnoredFalse(
                projectId, memberId);
    }
}

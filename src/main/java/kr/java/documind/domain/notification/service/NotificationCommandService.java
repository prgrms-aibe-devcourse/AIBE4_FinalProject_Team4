package kr.java.documind.domain.notification.service;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.notification.model.entity.Notification;
import kr.java.documind.domain.notification.model.repository.NotificationRepository;
import kr.java.documind.global.exception.NotificationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void markRead(Long id, UUID memberId, UUID projectId) {
        Notification notification;
        if (projectId != null) {
            notification = notificationRepository
                    .findByIdAndReceiverIdAndProjectId(id, memberId, projectId)
                    .orElseThrow(NotificationNotFoundException::new);
        } else {
            notification = notificationRepository
                    .findByIdAndReceiverId(id, memberId)
                    .orElseThrow(NotificationNotFoundException::new);
        }
        notification.markRead();
    }

    @Transactional
    public void markIgnored(Long id, UUID memberId, UUID projectId) {
        Notification notification;
        if (projectId != null) {
            notification = notificationRepository
                    .findByIdAndReceiverIdAndProjectId(id, memberId, projectId)
                    .orElseThrow(NotificationNotFoundException::new);
        } else {
            notification = notificationRepository
                    .findByIdAndReceiverId(id, memberId)
                    .orElseThrow(NotificationNotFoundException::new);
        }
        notification.markIgnored();
    }

    @Transactional
    public int markAllRead(UUID memberId, UUID projectId) {
        return notificationRepository.markAllReadDynamically(projectId, memberId);
    }

    @Transactional
    public void markReadBatch(List<Long> ids, UUID memberId) {
        if (ids == null || ids.isEmpty()) return;
        notificationRepository.markReadBatch(ids, memberId);
    }
}

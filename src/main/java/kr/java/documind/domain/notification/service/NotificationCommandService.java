package kr.java.documind.domain.notification.service;

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
    public void markRead(Long id, UUID memberId) {
        Notification notification =
                notificationRepository
                        .findByIdAndReceiverId(id, memberId)
                        .orElseThrow(NotificationNotFoundException::new);
        notification.markRead();
    }

    @Transactional
    public void markIgnored(Long id, UUID memberId) {
        Notification notification =
                notificationRepository
                        .findByIdAndReceiverId(id, memberId)
                        .orElseThrow(NotificationNotFoundException::new);
        notification.markIgnored();
    }

    @Transactional
    public int markAllRead(UUID memberId) {
        return notificationRepository.markAllReadByReceiverId(memberId);
    }
}

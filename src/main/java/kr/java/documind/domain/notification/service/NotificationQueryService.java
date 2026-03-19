package kr.java.documind.domain.notification.service;

import java.util.UUID;
import kr.java.documind.domain.notification.model.dto.response.NotificationResponse;
import kr.java.documind.domain.notification.model.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    public Page<NotificationResponse> getNotifications(UUID memberId, Pageable pageable) {
        return notificationRepository
                .findByReceiverIdOrderByCreatedAtDesc(memberId, pageable)
                .map(NotificationResponse::from);
    }

    public long getUnreadCount(UUID memberId) {
        return notificationRepository.countByReceiverIdAndIsReadFalseAndIsIgnoredFalse(memberId);
    }
}

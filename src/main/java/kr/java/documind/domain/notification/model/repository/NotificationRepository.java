package kr.java.documind.domain.notification.model.repository;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.notification.model.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository
        extends JpaRepository<Notification, Long>, NotificationRepositoryCustom {

    long countByProjectIdAndReceiverIdAndIsReadFalseAndIsIgnoredFalse(
            UUID projectId, UUID receiverId);

    Optional<Notification> findByIdAndReceiverIdAndProjectId(
            Long id, UUID receiverId, UUID projectId);

    @Modifying
    @Query(
            "UPDATE Notification n SET n.isRead = true"
                    + " WHERE n.projectId = :projectId AND n.receiverId = :receiverId"
                    + " AND n.isRead = false")
    int markAllReadByProjectIdAndReceiverId(
            @Param("projectId") UUID projectId, @Param("receiverId") UUID receiverId);
}

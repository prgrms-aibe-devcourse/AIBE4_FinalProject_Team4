package kr.java.documind.domain.notification.model.repository;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.notification.model.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = {"source"})
    Page<Notification> findByReceiverIdOrderByCreatedAtDesc(UUID receiverId, Pageable pageable);

    long countByReceiverIdAndIsReadFalseAndIsIgnoredFalse(UUID receiverId);

    Optional<Notification> findByIdAndReceiverId(Long id, UUID receiverId);

    @Modifying
    @Query(
            "UPDATE Notification n SET n.isRead = true"
                    + " WHERE n.receiverId = :receiverId AND n.isRead = false")
    int markAllReadByReceiverId(@Param("receiverId") UUID receiverId);
}

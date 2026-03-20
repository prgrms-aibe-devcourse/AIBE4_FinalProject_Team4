package kr.java.documind.domain.notification.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.notification.model.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository
        extends JpaRepository<Notification, Long>, NotificationRepositoryCustom {

    @Query("SELECT COUNT(n) FROM Notification n "
            + "WHERE n.receiverId = :receiverId "
            + "AND (:projectId IS NULL OR n.projectId = :projectId) "
            + "AND n.isRead = false AND n.isIgnored = false")
    long countUnread(@Param("projectId") UUID projectId, @Param("receiverId") UUID receiverId);


    Optional<Notification> findByIdAndReceiverIdAndProjectId(
            Long id, UUID receiverId, UUID projectId);

    Optional<Notification> findByIdAndReceiverId(Long id, UUID receiverId);

    // 동적 벌크 업데이트 (projectId가 null이면 전역 읽음 처리)
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE Notification n SET n.isRead = true "
                    + "WHERE n.receiverId = :receiverId "
                    + "AND (:projectId IS NULL OR n.projectId = :projectId) "
                    + "AND n.isRead = false")
    int markAllReadDynamically(
            @Param("projectId") UUID projectId, @Param("receiverId") UUID receiverId);

    // 화면 노출에 의한 다중 읽음 처리 (Batch)
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE Notification n SET n.isRead = true "
                    + "WHERE n.id IN :ids "
                    + "AND n.receiverId = :receiverId "
                    + "AND n.isRead = false")
    int markReadBatch(@Param("ids") List<Long> ids, @Param("receiverId") UUID receiverId);
}

package kr.java.documind.domain.notification.listener;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.notification.event.DocumentNotificationEvent;
import kr.java.documind.domain.notification.event.IssueNotificationEvent;
import kr.java.documind.domain.notification.event.LogNotificationEvent;
import kr.java.documind.domain.notification.event.NotificationEvent;
import kr.java.documind.domain.patchnote.event.PatchNoteNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSyncListener {

    private final JdbcTemplate jdbcTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onIssueNotification(IssueNotificationEvent event) {
        persist(event, event.severity());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLogNotification(LogNotificationEvent event) {
        persist(event, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDocumentNotification(DocumentNotificationEvent event) {
        persist(event, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPatchNoteNotification(PatchNoteNotificationEvent event) {
        persist(event, null);
    }

    private void persist(NotificationEvent event, IssueSeverity severity) {
        if (event.receiverIds().isEmpty()) {
            return;
        }

        String sql =
                """
                INSERT INTO notification (project_id, receiver_id, source_id, event_type, title, message,
                    severity, is_toast, is_read, is_ignored, related_url, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, false, false, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;

        List<UUID> receivers = event.receiverIds();

        jdbcTemplate.batchUpdate(
                sql,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setObject(1, event.projectId(), Types.OTHER);
                        ps.setObject(2, receivers.get(i), Types.OTHER);
                        ps.setLong(3, event.sourceId());
                        ps.setString(4, event.eventType().name());
                        ps.setString(5, event.title());
                        ps.setString(6, event.message());
                        ps.setString(7, severity != null ? severity.getValue() : null);
                        ps.setBoolean(8, event.isToast());
                        ps.setString(9, event.relatedUrl());
                    }

                    @Override
                    public int getBatchSize() {
                        return receivers.size();
                    }
                });

        log.debug(
                "[Notification] 알림 저장 완료 - eventType: {}, receiverCount: {}",
                event.eventType(),
                receivers.size());
    }
}

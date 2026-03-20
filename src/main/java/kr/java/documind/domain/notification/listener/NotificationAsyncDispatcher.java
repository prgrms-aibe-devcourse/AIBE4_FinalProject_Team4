package kr.java.documind.domain.notification.listener;

import java.util.HashMap;
import java.util.Map;
import kr.java.documind.domain.issue.event.IssueNotificationEvent;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.notification.event.DocumentNotificationEvent;
import kr.java.documind.domain.notification.event.LogNotificationEvent;
import kr.java.documind.domain.notification.event.NotificationEvent;
import kr.java.documind.domain.patchnote.event.PatchNoteNotificationEvent;
import kr.java.documind.domain.notification.infrastructure.NotificationSseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationAsyncDispatcher {

    private final NotificationSseManager sseManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueNotification(IssueNotificationEvent event) {
        if (!event.isToast()) {
            return;
        }
        dispatch(event, "issue", event.severity());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLogNotification(LogNotificationEvent event) {
        if (!event.isToast()) {
            return;
        }
        dispatch(event, "log", null);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentNotification(DocumentNotificationEvent event) {
        if (!event.isToast()) {
            return;
        }
        dispatch(event, "rag", null);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPatchNoteNotification(PatchNoteNotificationEvent event) {
        if (!event.isToast()) {
            return;
        }
        dispatch(event, "patchnote", null);
    }

    private void dispatch(NotificationEvent event, String type, IssueSeverity severity) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("title", event.title());
        payload.put("message", event.message());
        if (severity != null) {
            payload.put("severity", severity.getValue().toLowerCase());
        }
        payload.put("relatedUrl", event.relatedUrl());

        event.receiverIds()
                .forEach(
                        memberId -> {
                            if (sseManager.isConnected(memberId)) {
                                sseManager.send(memberId, payload);
                            }
                        });

        log.debug(
                "[Notification SSE] 푸시 완료 - type: {}, receiverCount: {}",
                type,
                event.receiverIds().size());
    }
}

package kr.java.documind.domain.notification.infrastructure;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
public class NotificationSseManager {

    private static final long TIMEOUT = 180_000L;

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID memberId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);

        SseEmitter old = emitters.put(memberId, emitter);
        if (old != null) {
            old.complete();
        }

        emitter.onCompletion(() -> emitters.remove(memberId, emitter));
        emitter.onTimeout(() -> emitters.remove(memberId, emitter));
        emitter.onError(e -> emitters.remove(memberId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
        } catch (IOException e) {
            log.warn("[SSE] 연결 이벤트 전송 실패 - memberId: {}", memberId, e);
            emitters.remove(memberId, emitter);
        }

        return emitter;
    }

    public void send(UUID memberId, Object payload) {
        SseEmitter emitter = emitters.get(memberId);
        if (emitter == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event().name("alarm-toast").data(payload));
        } catch (IOException e) {
            log.warn("[SSE] 알림 전송 실패 - memberId: {}", memberId, e);
            emitters.remove(memberId, emitter);
            emitter.completeWithError(e);
        }
    }

    public boolean isConnected(UUID memberId) {
        return emitters.containsKey(memberId);
    }
}

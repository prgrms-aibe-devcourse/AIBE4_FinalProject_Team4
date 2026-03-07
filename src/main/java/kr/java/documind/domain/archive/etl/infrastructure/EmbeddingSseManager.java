package kr.java.documind.domain.archive.etl.infrastructure;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.java.documind.domain.archive.etl.model.enums.EmbeddingStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
public class EmbeddingSseManager {

    private static final long TIMEOUT = 120_000L;

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long sourceId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);

        SseEmitter oldEmitter = emitters.put(sourceId, emitter);
        if (oldEmitter != null) {
            oldEmitter.complete();
        }

        emitter.onCompletion(() -> emitters.remove(sourceId, emitter));
        emitter.onTimeout(() -> emitters.remove(sourceId, emitter));
        emitter.onError(e -> emitters.remove(sourceId, emitter));

        return emitter;
    }

    public void send(Long sourceId, EmbeddingStatus status) {
        SseEmitter emitter = emitters.get(sourceId);
        if (emitter == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event().name("embedding-status").data(status.name()));
            if (status == EmbeddingStatus.SUCCESS || status == EmbeddingStatus.FAILED) {
                emitter.complete();
            }
        } catch (IOException e) {
            log.warn("[SSE] 이벤트 전송 실패 - sourceId: {}", sourceId, e);
            emitters.remove(sourceId, emitter);
            emitter.completeWithError(e);
        }
    }
}

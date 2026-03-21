package kr.java.documind.domain.patchnote.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import kr.java.documind.domain.patchnote.model.dto.DraftResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

/**
 * 패치노트 초안 생성 전용 SSE 이벤트 직렬화 컴포넌트.
 *
 * <p>프론트엔드가 기대하는 이벤트 계약을 한 곳에서 관리한다.
 *
 * <pre>
 * progress         → {"step": "BUILDING_CONTEXT" | "GENERATING"}
 * sources          → {"refs": [...]}
 * token            → {"content": "..."}
 * context_overflow → {"beforePercent": N, "afterPercent": N, "recommendedPercent": 100}
 * done             → {"cleanedContent": "...", "sourceRefs": [...]}
 * error            → {"message": "..."}
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatchNoteSseConverter {

    private final ObjectMapper objectMapper;

    public ServerSentEvent<String> progressEvent(String step) {
        return createEvent("progress", Map.of("step", step));
    }

    public ServerSentEvent<String> sourcesEvent(List<String> refs) {
        return createEvent("sources", Map.of("refs", refs));
    }

    public ServerSentEvent<String> tokenEvent(String content) {
        return createEvent("token", Map.of("content", content));
    }

    public ServerSentEvent<String> contextOverflowEvent(int beforePercent, int afterPercent) {
        return createEvent(
                "context_overflow",
                Map.of(
                        "beforePercent", beforePercent,
                        "afterPercent", afterPercent,
                        "recommendedPercent", 100));
    }

    public ServerSentEvent<String> doneEvent(DraftResult result) {
        return createEvent(
                "done",
                Map.of(
                        "cleanedContent", result.cleanedContent(),
                        "sourceRefs", result.sourceRefs()));
    }

    public ServerSentEvent<String> errorEvent(String message) {
        return createEvent("error", Map.of("message", message));
    }

    private ServerSentEvent<String> createEvent(String eventName, Object data) {
        try {
            return ServerSentEvent.<String>builder()
                    .event(eventName)
                    .data(objectMapper.writeValueAsString(data))
                    .build();
        } catch (JsonProcessingException e) {
            log.error("SSE 이벤트 직렬화 실패 — event={}", eventName, e);
            return ServerSentEvent.<String>builder().event(eventName).data("{}").build();
        }
    }
}

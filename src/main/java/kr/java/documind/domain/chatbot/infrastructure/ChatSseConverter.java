package kr.java.documind.domain.chatbot.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import kr.java.documind.domain.chatbot.model.dto.response.ReferenceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSseConverter {

    private final ObjectMapper objectMapper;

    public ServerSentEvent<String> tokenEvent(ChatClientResponse response) {
        return extractText(response).map(text -> createEvent("token", text)).orElse(null);
    }

    public ServerSentEvent<String> referencesEvent(List<ReferenceResponse> refs) {
        if (refs.isEmpty()) {
            return null;
        }
        try {
            return createEvent("references", objectMapper.writeValueAsString(refs));
        } catch (JsonProcessingException e) {
            log.error("참조 문서 직렬화 실패", e);
            return null;
        }
    }

    public ServerSentEvent<String> errorEvent(String message) {
        return createEvent("error", message);
    }

    public ServerSentEvent<String> doneEvent(String scopeDescription) {
        return createEvent("done", scopeDescription);
    }

    private Optional<String> extractText(ChatClientResponse response) {
        return Optional.ofNullable(response.chatResponse())
                .map(r -> r.getResult())
                .map(r -> r.getOutput())
                .map(r -> r.getText())
                .filter(text -> !text.isEmpty());
    }

    private ServerSentEvent<String> createEvent(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }
}

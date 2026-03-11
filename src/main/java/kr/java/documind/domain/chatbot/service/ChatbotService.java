package kr.java.documind.domain.chatbot.service;

import kr.java.documind.domain.chatbot.model.dto.request.ChatRequest;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatbotService {

    public Flux<ServerSentEvent<String>> chat(String conversationId, ChatRequest request) {
        return null;
    }
}

package kr.java.documind.domain.chatbot.service;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.chatbot.model.dto.response.ChatModelInfoResponse;
import kr.java.documind.domain.chatbot.properties.SupportedChatModels;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatbotMetaService {

    private final SupportedChatModels supportedChatModels;

    public List<ChatModelInfoResponse> getChatModels() {
        String defaultModel = supportedChatModels.defaultModel();
        return supportedChatModels.models().stream()
                .map(
                        model ->
                                new ChatModelInfoResponse(
                                        (model.provider().name()),
                                        model.alias(),
                                        model.displayName(),
                                        model.alias().equals(defaultModel)))
                .toList();
    }

    public List<String> getGroupNames(UUID projectId) {
        return null;
    }

    public List<String> getCategoryNames(UUID projectId) {
        return null;
    }
}

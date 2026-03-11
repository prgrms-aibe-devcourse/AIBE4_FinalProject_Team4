package kr.java.documind.domain.chatbot.service;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.chatbot.model.dto.response.ChatModelInfoResponse;
import org.springframework.stereotype.Service;

@Service
public class ChatbotMetaService {

    public List<ChatModelInfoResponse> getChatModels() {
        return null;
    }

    public List<String> getGroupNames(UUID projectId) {
        return null;
    }

    public List<String> getCategoryNames(UUID projectId) {
        return null;
    }

}

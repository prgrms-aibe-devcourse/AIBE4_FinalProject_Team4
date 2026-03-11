package kr.java.documind.domain.chatbot.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat")
public record SupportedChatModel(String defaultModel, List<ChatModelInfo> models) {}

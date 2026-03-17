package kr.java.documind.domain.chatbot.properties;

public record ChatModelInfo(
        ChatProvider provider,
        String modelId,
        String alias,
        String displayName,
        boolean reasoning) {}

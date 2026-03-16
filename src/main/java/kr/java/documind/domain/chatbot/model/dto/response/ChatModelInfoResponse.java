package kr.java.documind.domain.chatbot.model.dto.response;

public record ChatModelInfoResponse(
        String provider, String alias, String displayName, boolean defaultModel) {}

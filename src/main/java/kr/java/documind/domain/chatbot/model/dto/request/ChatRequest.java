package kr.java.documind.domain.chatbot.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "AI 모델을 선택해 주세요.") String modelAlias,
        String userSystemMessage,
        @NotBlank(message = "메시지를 입력해 주세요.") String userMessage,
        String groupName,
        String categoryName,
        Long documentId) {}

package kr.java.documind.domain.chatbot.model.vo;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

public record ResolvedChatModel(ChatModel chatModel, ChatOptions chatOptions) {}

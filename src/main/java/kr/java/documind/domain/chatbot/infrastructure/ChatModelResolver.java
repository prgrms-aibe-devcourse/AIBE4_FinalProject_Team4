package kr.java.documind.domain.chatbot.infrastructure;

import java.util.EnumMap;
import java.util.Map;
import kr.java.documind.domain.chatbot.model.vo.ResolvedChatModel;
import kr.java.documind.domain.chatbot.properties.ChatModelInfo;
import kr.java.documind.domain.chatbot.properties.ChatProvider;
import kr.java.documind.domain.chatbot.properties.SupportedChatModels;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.InternalServerException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ChatModelResolver {

    private final Map<ChatProvider, ChatModel> chatModels;
    private final SupportedChatModels supportedChatModels;

    public ChatModelResolver(
            @Nullable OllamaChatModel ollamaChatModel,
            OpenAiChatModel openAiChatModel,
            GoogleGenAiChatModel googleGenAiChatModel,
            SupportedChatModels supportedChatModels) {
        this.chatModels = new EnumMap<>(ChatProvider.class);
        if (ollamaChatModel != null) {
            chatModels.put(ChatProvider.OLLAMA, ollamaChatModel);
        }
        chatModels.put(ChatProvider.OPENAI, openAiChatModel);
        chatModels.put(ChatProvider.GOOGLE_GENAI, googleGenAiChatModel);
        this.supportedChatModels = supportedChatModels;
    }

    public ResolvedChatModel resolve(String alias) {
        String target =
                (alias != null && !alias.isBlank()) ? alias : supportedChatModels.defaultModel();
        return resolveByAlias(target);
    }

    public ResolvedChatModel resolveForPatchNote(String alias) {
        String target =
                (alias != null && !alias.isBlank())
                        ? alias
                        : supportedChatModels.patchnoteDefaultModel();
        return resolveByAlias(target);
    }

    private ResolvedChatModel resolveByAlias(String alias) {
        ChatModelInfo modelInfo = findByAlias(alias);
        ChatModel chatModel = getChatModel(modelInfo.provider());
        ChatOptions chatOptions = buildOptions(modelInfo);
        return new ResolvedChatModel(chatModel, chatOptions);
    }

    private ChatModelInfo findByAlias(String alias) {
        return supportedChatModels.models().stream()
                .filter(model -> model.alias().equals(alias))
                .findFirst()
                .orElseThrow(
                        () -> new BadRequestException(String.format("지원하지 않는 모델입니다: %s", alias)));
    }

    private ChatModel getChatModel(ChatProvider provider) {
        ChatModel chatModel = chatModels.get(provider);
        if (chatModel == null) {
            throw new InternalServerException(String.format("%s 프로바이더가 비활성화되어 있습니다.", provider));
        }
        return chatModel;
    }

    private ChatOptions buildOptions(ChatModelInfo model) {
        return switch (model.provider()) {
            case OLLAMA -> OllamaChatOptions.builder().model(model.modelId()).build();
            case OPENAI -> {
                OpenAiChatOptions.Builder builder =
                        OpenAiChatOptions.builder().model(model.modelId());
                if (model.reasoning()) {
                    builder.temperature(1.0);
                }
                yield builder.build();
            }
            case GOOGLE_GENAI -> GoogleGenAiChatOptions.builder().model(model.modelId()).build();
        };
    }
}

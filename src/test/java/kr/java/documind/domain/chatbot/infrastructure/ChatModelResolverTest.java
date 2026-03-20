package kr.java.documind.domain.chatbot.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import kr.java.documind.domain.chatbot.model.vo.ResolvedChatModel;
import kr.java.documind.domain.chatbot.properties.ChatModelInfo;
import kr.java.documind.domain.chatbot.properties.ChatProvider;
import kr.java.documind.domain.chatbot.properties.SupportedChatModels;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.InternalServerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

@DisplayName("ChatModelResolver")
class ChatModelResolverTest {

    private final OllamaChatModel ollamaChatModel = mock(OllamaChatModel.class);
    private final OpenAiChatModel openAiChatModel = mock(OpenAiChatModel.class);
    private final GoogleGenAiChatModel googleGenAiChatModel = mock(GoogleGenAiChatModel.class);

    private ChatModelResolver createResolver(SupportedChatModels supportedChatModels) {
        return new ChatModelResolver(
                ollamaChatModel, openAiChatModel, googleGenAiChatModel, supportedChatModels);
    }

    private ChatModelInfo openAiModel(String alias, String modelId, boolean reasoning) {
        return new ChatModelInfo(ChatProvider.OPENAI, modelId, alias, "OpenAI " + alias, reasoning);
    }

    private ChatModelInfo googleModel(String alias, String modelId) {
        return new ChatModelInfo(
                ChatProvider.GOOGLE_GENAI, modelId, alias, "Google " + alias, false);
    }

    private ChatModelInfo ollamaModel(String alias, String modelId) {
        return new ChatModelInfo(ChatProvider.OLLAMA, modelId, alias, "Ollama " + alias, false);
    }

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("alias로 모델을 선택하면 해당 ChatModel이 반환된다")
        void resolve_WithAlias_ReturnsMatchingModel() {
            SupportedChatModels supported =
                    new SupportedChatModels(
                            "gpt-4o", "gpt-4o", List.of(openAiModel("gpt-4o", "gpt-4o", false)));
            ChatModelResolver resolver = createResolver(supported);

            ResolvedChatModel result = resolver.resolve("gpt-4o");

            assertThat(result.chatModel()).isEqualTo(openAiChatModel);
        }

        @Test
        @DisplayName("alias가 null이면 기본 모델이 선택된다")
        void resolve_NullAlias_UsesDefault() {
            SupportedChatModels supported =
                    new SupportedChatModels(
                            "gpt-4o", "gpt-4o", List.of(openAiModel("gpt-4o", "gpt-4o", false)));
            ChatModelResolver resolver = createResolver(supported);

            ResolvedChatModel result = resolver.resolve(null);

            assertThat(result.chatModel()).isEqualTo(openAiChatModel);
        }

        @Test
        @DisplayName("alias가 빈 문자열이면 기본 모델이 선택된다")
        void resolve_BlankAlias_UsesDefault() {
            SupportedChatModels supported =
                    new SupportedChatModels(
                            "gpt-4o", "gpt-4o", List.of(openAiModel("gpt-4o", "gpt-4o", false)));
            ChatModelResolver resolver = createResolver(supported);

            ResolvedChatModel result = resolver.resolve("  ");

            assertThat(result.chatModel()).isEqualTo(openAiChatModel);
        }

        @Test
        @DisplayName("지원하지 않는 alias이면 BadRequestException이 발생한다")
        void resolve_UnsupportedAlias_ThrowsBadRequest() {
            SupportedChatModels supported =
                    new SupportedChatModels(
                            "gpt-4o", "gpt-4o", List.of(openAiModel("gpt-4o", "gpt-4o", false)));
            ChatModelResolver resolver = createResolver(supported);

            assertThatThrownBy(() -> resolver.resolve("unknown-model"))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Google 모델을 선택하면 GoogleGenAiChatModel이 반환된다")
        void resolve_GoogleProvider_ReturnsGoogleModel() {
            SupportedChatModels supported =
                    new SupportedChatModels(
                            "gemini", "gemini", List.of(googleModel("gemini", "gemini-pro")));
            ChatModelResolver resolver = createResolver(supported);

            ResolvedChatModel result = resolver.resolve("gemini");

            assertThat(result.chatModel()).isEqualTo(googleGenAiChatModel);
        }

        @Test
        @DisplayName("Ollama 모델을 선택하면 OllamaChatModel이 반환된다")
        void resolve_OllamaProvider_ReturnsOllamaModel() {
            SupportedChatModels supported =
                    new SupportedChatModels(
                            "llama", "llama", List.of(ollamaModel("llama", "llama3")));
            ChatModelResolver resolver = createResolver(supported);

            ResolvedChatModel result = resolver.resolve("llama");

            assertThat(result.chatModel()).isEqualTo(ollamaChatModel);
        }
    }

    @Nested
    @DisplayName("buildOptions")
    class BuildOptions {

        @Test
        @DisplayName("OpenAI reasoning 모델이면 temperature가 1.0으로 설정된다")
        void resolve_OpenAiReasoning_SetsTemperature() {
            SupportedChatModels supported =
                    new SupportedChatModels("o1", "o1", List.of(openAiModel("o1", "o1", true)));
            ChatModelResolver resolver = createResolver(supported);

            ResolvedChatModel result = resolver.resolve("o1");

            assertThat(result.chatOptions()).isInstanceOf(OpenAiChatOptions.class);
            OpenAiChatOptions options = (OpenAiChatOptions) result.chatOptions();
            assertThat(options.getTemperature()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("OpenAI 일반 모델이면 temperature가 설정되지 않는다")
        void resolve_OpenAiNonReasoning_NoTemperature() {
            SupportedChatModels supported =
                    new SupportedChatModels(
                            "gpt-4o", "gpt-4o", List.of(openAiModel("gpt-4o", "gpt-4o", false)));
            ChatModelResolver resolver = createResolver(supported);

            ResolvedChatModel result = resolver.resolve("gpt-4o");

            assertThat(result.chatOptions()).isInstanceOf(OpenAiChatOptions.class);
            OpenAiChatOptions options = (OpenAiChatOptions) result.chatOptions();
            assertThat(options.getTemperature()).isNull();
        }
    }

    @Nested
    @DisplayName("비활성 프로바이더")
    class DisabledProvider {

        @Test
        @DisplayName("Ollama가 null이면 InternalServerException이 발생한다")
        void resolve_OllamaDisabled_ThrowsInternalServer() {
            SupportedChatModels supported =
                    new SupportedChatModels(
                            "llama", "llama", List.of(ollamaModel("llama", "llama3")));
            ChatModelResolver resolver =
                    new ChatModelResolver(null, openAiChatModel, googleGenAiChatModel, supported);

            assertThatThrownBy(() -> resolver.resolve("llama"))
                    .isInstanceOf(InternalServerException.class);
        }
    }
}

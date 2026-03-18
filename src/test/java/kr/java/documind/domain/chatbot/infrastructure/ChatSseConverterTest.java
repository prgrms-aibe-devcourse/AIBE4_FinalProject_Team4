package kr.java.documind.domain.chatbot.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import kr.java.documind.domain.chatbot.model.dto.response.ReferenceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.codec.ServerSentEvent;

@DisplayName("ChatSseConverter")
class ChatSseConverterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatSseConverter converter = new ChatSseConverter(objectMapper);

    @Nested
    @DisplayName("tokenEvent")
    class TokenEvent {

        @Test
        @DisplayName("텍스트가 있으면 token 이벤트를 반환한다")
        void tokenEvent_WithText_ReturnsTokenEvent() {
            ChatClientResponse response = createChatClientResponse("안녕하세요");

            ServerSentEvent<String> result = converter.tokenEvent(response);

            assertThat(result).isNotNull();
            assertThat(result.event()).isEqualTo("token");
            assertThat(result.data()).isEqualTo("안녕하세요");
        }

        @Test
        @DisplayName("chatResponse가 null이면 null을 반환한다")
        void tokenEvent_NullChatResponse_ReturnsNull() {
            ChatClientResponse response = mock(ChatClientResponse.class);
            given(response.chatResponse()).willReturn(null);

            ServerSentEvent<String> result = converter.tokenEvent(response);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("텍스트가 빈 문자열이면 null을 반환한다")
        void tokenEvent_EmptyText_ReturnsNull() {
            ChatClientResponse response = createChatClientResponse("");

            ServerSentEvent<String> result = converter.tokenEvent(response);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("output이 null이면 null을 반환한다")
        void tokenEvent_NullOutput_ReturnsNull() {
            Generation generation = mock(Generation.class);
            given(generation.getOutput()).willReturn(null);
            ChatResponse chatResponse = mock(ChatResponse.class);
            given(chatResponse.getResult()).willReturn(generation);
            ChatClientResponse response = mock(ChatClientResponse.class);
            given(response.chatResponse()).willReturn(chatResponse);

            ServerSentEvent<String> result = converter.tokenEvent(response);

            assertThat(result).isNull();
        }

        private ChatClientResponse createChatClientResponse(String text) {
            AssistantMessage message = new AssistantMessage(text);
            Generation generation = new Generation(message);
            ChatResponse chatResponse = new ChatResponse(List.of(generation));
            ChatClientResponse response = mock(ChatClientResponse.class);
            given(response.chatResponse()).willReturn(chatResponse);
            return response;
        }
    }

    @Nested
    @DisplayName("referencesEvent")
    class ReferencesEvent {

        @Test
        @DisplayName("참조 목록이 있으면 references 이벤트를 반환한다")
        void referencesEvent_WithRefs_ReturnsEvent() {
            List<ReferenceResponse> refs =
                    List.of(new ReferenceResponse(1L, "문서A", "pdf", "v1.0.0", 3, "텍스트"));

            ServerSentEvent<String> result = converter.referencesEvent(refs);

            assertThat(result).isNotNull();
            assertThat(result.event()).isEqualTo("references");
            assertThat(result.data()).contains("문서A");
        }

        @Test
        @DisplayName("빈 목록이면 null을 반환한다")
        void referencesEvent_EmptyRefs_ReturnsNull() {
            ServerSentEvent<String> result = converter.referencesEvent(List.of());

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("JSON에 모든 필드가 포함된다")
        void referencesEvent_ContainsAllFields() {
            List<ReferenceResponse> refs =
                    List.of(new ReferenceResponse(1L, "문서A", "pdf", "v1.0.0", 5, "청크"));

            ServerSentEvent<String> result = converter.referencesEvent(refs);

            String data = result.data();
            assertThat(data).contains("\"documentId\":1");
            assertThat(data).contains("\"documentName\":\"문서A\"");
            assertThat(data).contains("\"extension\":\"pdf\"");
            assertThat(data).contains("\"version\":\"v1.0.0\"");
            assertThat(data).contains("\"pageNumber\":5");
            assertThat(data).contains("\"chunkText\":\"청크\"");
        }
    }

    @Nested
    @DisplayName("errorEvent")
    class ErrorEvent {

        @Test
        @DisplayName("error 이벤트를 반환한다")
        void errorEvent_ReturnsErrorEvent() {
            ServerSentEvent<String> result = converter.errorEvent("오류 메시지");

            assertThat(result.event()).isEqualTo("error");
            assertThat(result.data()).isEqualTo("오류 메시지");
        }
    }

    @Nested
    @DisplayName("doneEvent")
    class DoneEvent {

        @Test
        @DisplayName("done 이벤트를 반환한다")
        void doneEvent_ReturnsDoneEvent() {
            ServerSentEvent<String> result = converter.doneEvent();

            assertThat(result.event()).isEqualTo("done");
            assertThat(result.data()).isEmpty();
        }
    }
}

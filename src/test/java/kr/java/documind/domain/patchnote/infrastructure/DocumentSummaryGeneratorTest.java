package kr.java.documind.domain.patchnote.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import kr.java.documind.domain.chatbot.infrastructure.ChatModelResolver;
import kr.java.documind.domain.chatbot.model.vo.ResolvedChatModel;
import kr.java.documind.domain.patchnote.model.dto.DocumentSummaryResult;
import kr.java.documind.global.util.PromptUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentSummaryGenerator 단위 테스트")
class DocumentSummaryGeneratorTest {

    // ObjectMapper는 실제 JSON 파싱이 필요하므로 실제 인스턴스 사용
    private final ObjectMapper realObjectMapper = new ObjectMapper();

    @Mock private ChatModelResolver chatModelResolver;
    @Mock private PromptUtil promptUtil;

    private DocumentSummaryGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DocumentSummaryGenerator(chatModelResolver, promptUtil, realObjectMapper);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 픽스처 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    /** ChatClient 정적 빌더 체인을 완전히 모킹하고 지정된 응답 문자열을 반환하도록 설정 */
    private void stubChatClientChain(
            ChatModel mockChatModel,
            ChatOptions mockChatOptions,
            MockedStatic<ChatClient> chatClientStatic,
            String responseText) {

        ChatClient.Builder mockBuilder = mock(ChatClient.Builder.class);
        ChatClient mockChatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec mockSpec =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec mockCallSpec = mock(ChatClient.CallResponseSpec.class);

        chatClientStatic
                .when(() -> ChatClient.builder(mockChatModel))
                .thenReturn(mockBuilder);
        given(mockBuilder.defaultOptions(mockChatOptions)).willReturn(mockBuilder);
        given(mockBuilder.build()).willReturn(mockChatClient);
        given(mockChatClient.prompt()).willReturn(mockSpec);
        given(mockSpec.user(anyString())).willReturn(mockSpec);
        given(mockSpec.call()).willReturn(mockCallSpec);
        given(mockCallSpec.content()).willReturn(responseText);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // generate() — fallback 처리
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generate() - fallback 처리")
    class GenerateFallback {

        @Test
        @DisplayName("fallback: chatModelResolver.resolve() 예외 → title/summary=documentName, affectsPlayer=true")
        void generate_chatModelResolver예외_fallback반환() {
            // Given
            given(chatModelResolver.resolve(null)).willThrow(new RuntimeException("모델 없음"));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            // When
            DocumentSummaryResult result =
                    generator.generate("오류문서.pdf", "그룹", "FIX", List.of("청크1"));

            // Then
            assertThat(result.title()).isEqualTo("오류문서.pdf");
            assertThat(result.summary()).isEqualTo("오류문서.pdf");
            assertThat(result.affectsPlayer()).isTrue();
        }

        @Test
        @DisplayName("fallback: promptUtil.render() 예외 → fallback 반환 (LLM 미호출)")
        void generate_promptUtil예외_fallback반환() {
            // Given
            given(promptUtil.render(anyString(), any()))
                    .willThrow(new RuntimeException("템플릿 없음"));

            // When
            DocumentSummaryResult result =
                    generator.generate("프롬프트오류.pdf", "그룹", "CHANGE", List.of("청크"));

            // Then
            assertThat(result.title()).isEqualTo("프롬프트오류.pdf");
            assertThat(result.affectsPlayer()).isTrue();
        }

        @Test
        @DisplayName("fallback: category=null → categoryFromLlm 빈 문자열")
        void generate_category_null_fallback_categoryFromLlm빈문자열() {
            // Given
            given(chatModelResolver.resolve(null)).willThrow(new RuntimeException("오류"));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            // When
            DocumentSummaryResult result =
                    generator.generate("문서.pdf", "그룹", null, List.of("청크"));

            // Then
            assertThat(result.categoryFromLlm()).isEqualTo("");
        }

        @Test
        @DisplayName("fallback: category 값 → categoryFromLlm에 대문자로 반영")
        void generate_category있음_fallback_categoryFromLlm대문자() {
            // Given
            given(chatModelResolver.resolve(null)).willThrow(new RuntimeException("오류"));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            // When
            DocumentSummaryResult result =
                    generator.generate("문서.pdf", "그룹", "change", List.of("청크"));

            // Then
            assertThat(result.categoryFromLlm()).isEqualTo("CHANGE");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // generate() — JSON 파싱 (정상 경로)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generate() - JSON 파싱 (LLM 응답 정상)")
    class GenerateJsonParsing {

        @Test
        @DisplayName("정상 JSON: title/summary/category/isUserFacing 모두 정상 파싱")
        void generate_정상JSON_모든필드파싱() {
            // Given
            ChatModel mockChatModel = mock(ChatModel.class);
            ChatOptions mockChatOptions = mock(ChatOptions.class);
            given(chatModelResolver.resolve(null))
                    .willReturn(new ResolvedChatModel(mockChatModel, mockChatOptions));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            String json =
                    """
                    {"title":"패치 업데이트","summary":"유저에게 중요한 변경","category":"CHANGE","isUserFacing":false}
                    """;

            try (MockedStatic<ChatClient> chatClientStatic = mockStatic(ChatClient.class)) {
                stubChatClientChain(mockChatModel, mockChatOptions, chatClientStatic, json);

                // When
                DocumentSummaryResult result =
                        generator.generate("패치노트.pdf", "그룹", "FIX", List.of("청크"));

                // Then
                assertThat(result.title()).isEqualTo("패치 업데이트");
                assertThat(result.summary()).isEqualTo("유저에게 중요한 변경");
                assertThat(result.categoryFromLlm()).isEqualTo("CHANGE");
                assertThat(result.affectsPlayer()).isFalse();
            }
        }

        @Test
        @DisplayName("isUserFacing=true → affectsPlayer=true")
        void generate_isUserFacing_true_affectsPlayer_true() {
            // Given
            ChatModel mockChatModel = mock(ChatModel.class);
            ChatOptions mockChatOptions = mock(ChatOptions.class);
            given(chatModelResolver.resolve(null))
                    .willReturn(new ResolvedChatModel(mockChatModel, mockChatOptions));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            String json =
                    """
                    {"title":"제목","summary":"요약","category":"FIX","isUserFacing":true}
                    """;

            try (MockedStatic<ChatClient> chatClientStatic = mockStatic(ChatClient.class)) {
                stubChatClientChain(mockChatModel, mockChatOptions, chatClientStatic, json);

                // When
                DocumentSummaryResult result =
                        generator.generate("문서.pdf", "그룹", "FIX", List.of("청크"));

                // Then
                assertThat(result.affectsPlayer()).isTrue();
            }
        }

        @Test
        @DisplayName("isUserFacing 필드 없음 → affectsPlayer=true (보수적 fallback)")
        void generate_isUserFacing_없음_affectsPlayer_true() {
            // Given
            ChatModel mockChatModel = mock(ChatModel.class);
            ChatOptions mockChatOptions = mock(ChatOptions.class);
            given(chatModelResolver.resolve(null))
                    .willReturn(new ResolvedChatModel(mockChatModel, mockChatOptions));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            String json = """
                    {"title":"제목","summary":"요약","category":"NEW"}
                    """;

            try (MockedStatic<ChatClient> chatClientStatic = mockStatic(ChatClient.class)) {
                stubChatClientChain(mockChatModel, mockChatOptions, chatClientStatic, json);

                // When
                DocumentSummaryResult result =
                        generator.generate("문서.pdf", "그룹", "NEW", List.of("청크"));

                // Then
                assertThat(result.affectsPlayer()).isTrue();
            }
        }

        @Test
        @DisplayName("title 빈 문자열 → documentName으로 대체")
        void generate_title빈문자열_documentName으로대체() {
            // Given
            ChatModel mockChatModel = mock(ChatModel.class);
            ChatOptions mockChatOptions = mock(ChatOptions.class);
            given(chatModelResolver.resolve(null))
                    .willReturn(new ResolvedChatModel(mockChatModel, mockChatOptions));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            String json = """
                    {"title":"","summary":"요약","category":"FIX","isUserFacing":true}
                    """;

            try (MockedStatic<ChatClient> chatClientStatic = mockStatic(ChatClient.class)) {
                stubChatClientChain(mockChatModel, mockChatOptions, chatClientStatic, json);

                // When
                DocumentSummaryResult result =
                        generator.generate("제목없는문서.pdf", "그룹", "FIX", List.of("청크"));

                // Then
                assertThat(result.title()).isEqualTo("제목없는문서.pdf");
            }
        }

        @Test
        @DisplayName("summary 빈 문자열 → documentName으로 대체")
        void generate_summary빈문자열_documentName으로대체() {
            // Given
            ChatModel mockChatModel = mock(ChatModel.class);
            ChatOptions mockChatOptions = mock(ChatOptions.class);
            given(chatModelResolver.resolve(null))
                    .willReturn(new ResolvedChatModel(mockChatModel, mockChatOptions));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            String json = """
                    {"title":"제목","summary":"","category":"FIX","isUserFacing":true}
                    """;

            try (MockedStatic<ChatClient> chatClientStatic = mockStatic(ChatClient.class)) {
                stubChatClientChain(mockChatModel, mockChatOptions, chatClientStatic, json);

                // When
                DocumentSummaryResult result =
                        generator.generate("요약없는문서.pdf", "그룹", "FIX", List.of("청크"));

                // Then
                assertThat(result.summary()).isEqualTo("요약없는문서.pdf");
            }
        }

        @Test
        @DisplayName("category 빈 문자열 → fallbackCategory로 대체")
        void generate_category빈문자열_fallbackCategory로대체() {
            // Given
            ChatModel mockChatModel = mock(ChatModel.class);
            ChatOptions mockChatOptions = mock(ChatOptions.class);
            given(chatModelResolver.resolve(null))
                    .willReturn(new ResolvedChatModel(mockChatModel, mockChatOptions));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            String json = """
                    {"title":"제목","summary":"요약","category":"","isUserFacing":true}
                    """;

            try (MockedStatic<ChatClient> chatClientStatic = mockStatic(ChatClient.class)) {
                stubChatClientChain(mockChatModel, mockChatOptions, chatClientStatic, json);

                // When
                DocumentSummaryResult result =
                        generator.generate("문서.pdf", "그룹", "maintenance", List.of("청크"));

                // Then
                assertThat(result.categoryFromLlm()).isEqualTo("MAINTENANCE");
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // generate() — JSON 파싱 실패
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generate() - JSON 파싱 실패 fallback")
    class GenerateJsonParseFallback {

        @Test
        @DisplayName("잘못된 JSON(비정형 텍스트) → fallback 반환")
        void generate_잘못된JSON_fallback반환() {
            // Given
            ChatModel mockChatModel = mock(ChatModel.class);
            ChatOptions mockChatOptions = mock(ChatOptions.class);
            given(chatModelResolver.resolve(null))
                    .willReturn(new ResolvedChatModel(mockChatModel, mockChatOptions));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            String invalidJson = "죄송합니다. 분석이 불가합니다.";

            try (MockedStatic<ChatClient> chatClientStatic = mockStatic(ChatClient.class)) {
                stubChatClientChain(
                        mockChatModel, mockChatOptions, chatClientStatic, invalidJson);

                // When
                DocumentSummaryResult result =
                        generator.generate("파싱실패문서.pdf", "그룹", "FIX", List.of("청크"));

                // Then
                assertThat(result.title()).isEqualTo("파싱실패문서.pdf");
                assertThat(result.affectsPlayer()).isTrue();
            }
        }

        @Test
        @DisplayName("마크다운 코드블록 포함 JSON → 코드블록 제거 후 정상 파싱")
        void generate_마크다운코드블록_제거후파싱() {
            // Given
            ChatModel mockChatModel = mock(ChatModel.class);
            ChatOptions mockChatOptions = mock(ChatOptions.class);
            given(chatModelResolver.resolve(null))
                    .willReturn(new ResolvedChatModel(mockChatModel, mockChatOptions));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            String markdownJson =
                    """
                    ```json
                    {"title":"마크다운제목","summary":"마크다운요약","category":"NEW","isUserFacing":false}
                    ```
                    """;

            try (MockedStatic<ChatClient> chatClientStatic = mockStatic(ChatClient.class)) {
                stubChatClientChain(
                        mockChatModel, mockChatOptions, chatClientStatic, markdownJson);

                // When
                DocumentSummaryResult result =
                        generator.generate("마크다운문서.pdf", "그룹", "NEW", List.of("청크"));

                // Then
                assertThat(result.title()).isEqualTo("마크다운제목");
                assertThat(result.categoryFromLlm()).isEqualTo("NEW");
                assertThat(result.affectsPlayer()).isFalse();
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // generate() — 컨텐츠 길이 제한 (CONTENT_CHAR_LIMIT = 3000)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generate() - 컨텐츠 길이 제한")
    class GenerateContentLimit {

        @Test
        @DisplayName("청크 합계 3000자 초과: 3000자까지만 LLM에 전달 후 fallback 반환")
        void generate_청크합계3000자초과_3000자까지만사용() {
            // Given — 각 청크 1500자, 합계 3000자 초과
            String chunk1 = "A".repeat(1500);
            String chunk2 = "B".repeat(1500);
            String chunk3 = "C".repeat(1500); // 3번째 청크는 잘려야 함

            given(chatModelResolver.resolve(null)).willThrow(new RuntimeException("오류"));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            // When
            DocumentSummaryResult result =
                    generator.generate(
                            "긴문서.pdf", "그룹", "FIX", List.of(chunk1, chunk2, chunk3));

            // Then — fallback이지만 예외 없이 처리
            assertThat(result.title()).isEqualTo("긴문서.pdf");
        }

        @Test
        @DisplayName("빈 청크 목록: 내용 없이 LLM 호출 → 예외 없이 처리")
        void generate_빈청크목록_예외없이처리() {
            // Given
            given(chatModelResolver.resolve(null)).willThrow(new RuntimeException("오류"));
            given(promptUtil.render(anyString(), any())).willReturn("프롬프트");

            // When
            DocumentSummaryResult result =
                    generator.generate("빈문서.pdf", "그룹", "FIX", List.of());

            // Then
            assertThat(result.title()).isEqualTo("빈문서.pdf");
            assertThat(result.affectsPlayer()).isTrue();
        }
    }
}

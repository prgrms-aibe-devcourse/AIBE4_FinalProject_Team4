package kr.java.documind.domain.chatbot.service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.chatbot.infrastructure.ChatModelResolver;
import kr.java.documind.domain.chatbot.infrastructure.ChatSseConverter;
import kr.java.documind.domain.chatbot.infrastructure.ReferenceExtractor;
import kr.java.documind.domain.chatbot.model.dto.request.ChatRequest;
import kr.java.documind.domain.chatbot.model.dto.response.ReferenceResponse;
import kr.java.documind.domain.chatbot.model.vo.ResolvedChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final FilterExpressionBuilder FILTER = new FilterExpressionBuilder();

    private final ChatModelResolver chatModelResolver;
    private final ChatSseConverter chatSseConverter;
    private final ReferenceExtractor referenceExtractor;
    private final DocumentMetadataManager documentMetadataManager;

    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
    private final SimpleLoggerAdvisor simpleLoggerAdvisor;

    @Value("classpath:prompts/system-message.st")
    private Resource systemMessageResource;

    private PromptTemplate systemPromptTemplate;

    @PostConstruct
    void init() {
        this.systemPromptTemplate = new PromptTemplate(systemMessageResource);
    }

    public Flux<ServerSentEvent<String>> chat(
            String conversationId, UUID projectId, ChatRequest request) {

        // 1. 필터 표현식 생성
        Filter.Expression filterExpression = buildFilterExpression(projectId, request);
        if (filterExpression == null) {
            return Flux.just(
                    chatSseConverter.errorEvent("검색 대상 문서가 없습니다."), chatSseConverter.doneEvent());
        }

        // 2. 모델 결정 & 시스템 메시지
        ResolvedChatModel resolved = chatModelResolver.resolve(request.modelAlias());
        String systemMessage = buildSystemMessage(request.userSystemMessage());

        // 3. 참조문서 홀더 (스트리밍 중 첫 추출 시 저장)
        AtomicReference<List<ReferenceResponse>> refsHolder = new AtomicReference<>();

        // 4. ChatClient 스트리밍
        Flux<ServerSentEvent<String>> tokenEvents =
                buildChatClient(resolved, conversationId, filterExpression, systemMessage, request)
                        .mapNotNull(
                                response -> {
                                    captureReferencesOnce(refsHolder, response);
                                    return chatSseConverter.tokenEvent(response);
                                });

        // 5. 종료 이벤트 (참조문서 + done)
        Flux<ServerSentEvent<String>> endEvents =
                Flux.defer(() -> buildEndEvents(refsHolder.get()));

        return tokenEvents
                .concatWith(endEvents)
                .onErrorResume(
                        e -> {
                            log.error("채팅 응답 생성 중 오류 발생", e);
                            return Flux.just(chatSseConverter.errorEvent("응답 생성 중 오류가 발생했습니다."));
                        });
    }

    private Filter.Expression buildFilterExpression(UUID projectId, ChatRequest request) {
        if (request.groupName() == null && request.categoryName() == null) {
            return FILTER.eq("project_id", projectId.toString()).build();
        }

        List<Long> sourceIds = resolveSourceIds(projectId, request);
        if (sourceIds.isEmpty()) {
            log.warn(
                    "스코프 필터링 대상 문서가 없음: groupName={}, categoryName={}",
                    request.groupName(),
                    request.categoryName());
            return null;
        }

        return FILTER.in("source_id", List.copyOf(sourceIds)).build();
    }

    private List<Long> resolveSourceIds(UUID projectId, ChatRequest request) {
        if (request.groupName() != null) {
            return documentMetadataManager.findIdsByProjectIdAndGroupName(
                    projectId, request.groupName());
        }
        return documentMetadataManager.findIdsByProjectIdAndCategory(
                projectId, request.categoryName());
    }

    private String buildSystemMessage(String userSystemMessage) {
        String message = userSystemMessage != null ? userSystemMessage.strip() : "";
        return systemPromptTemplate.render(Map.of("userSystemMessage", message)).strip();
    }

    private Flux<ChatClientResponse> buildChatClient(
            ResolvedChatModel resolved,
            String conversationId,
            Filter.Expression filterExpression,
            String systemMessage,
            ChatRequest request) {
        return ChatClient.builder(resolved.chatModel())
                .defaultOptions(resolved.chatOptions())
                .defaultAdvisors(
                        messageChatMemoryAdvisor, retrievalAugmentationAdvisor, simpleLoggerAdvisor)
                .build()
                .prompt()
                .system(systemMessage)
                .user(request.userMessage())
                .advisors(
                        advisor -> {
                            advisor.param(ChatMemory.CONVERSATION_ID, conversationId);
                            advisor.param(
                                    VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                                    filterExpression);
                        })
                .stream()
                .chatClientResponse();
    }

    private void captureReferencesOnce(
            AtomicReference<List<ReferenceResponse>> holder, ChatClientResponse response) {
        if (holder.get() != null) {
            return;
        }
        List<ReferenceResponse> refs = referenceExtractor.extract(response);
        if (!refs.isEmpty()) {
            holder.set(refs);
        }
    }

    private Flux<ServerSentEvent<String>> buildEndEvents(List<ReferenceResponse> refs) {
        if (refs == null) {
            return Flux.just(chatSseConverter.doneEvent());
        }
        ServerSentEvent<String> refEvent = chatSseConverter.referencesEvent(refs);
        return refEvent != null
                ? Flux.just(refEvent, chatSseConverter.doneEvent())
                : Flux.just(chatSseConverter.doneEvent());
    }
}

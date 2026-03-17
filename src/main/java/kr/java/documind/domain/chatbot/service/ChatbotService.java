package kr.java.documind.domain.chatbot.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
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
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final ChatModelResolver chatModelResolver;
    private final ChatSseConverter chatSseConverter;
    private final ReferenceExtractor referenceExtractor;
    private final DocumentMetadataManager documentMetadataManager;

    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
    private final SimpleLoggerAdvisor simpleLoggerAdvisor;

    @Value("classpath:prompts/system-message.st")
    private Resource systemMessageResource;

    public Flux<ServerSentEvent<String>> chat(
            String conversationId, UUID projectId, ChatRequest request) {

        // 1. 필터 표현식 생성
        String filterExpression = buildFilterExpression(projectId, request);
        if (filterExpression == null) {
            return Flux.just(
                    chatSseConverter.errorEvent("검색 대상 문서가 없습니다."), chatSseConverter.doneEvent());
        }

        // 2. 모델 결정 & 시스템 메시지
        ResolvedChatModel resolved = chatModelResolver.resolve(request.modelAlias());
        String systemMessage = buildSystemMessage(request.userSystemMessage());

        // 3. 참조문서 추출 상태 (null이면 아직 미추출)
        AtomicReference<List<ReferenceResponse>> refsHolder = new AtomicReference<>(null);

        // 4. ChatClient 스트리밍
        Flux<ServerSentEvent<String>> tokenEvents =
                ChatClient.builder(resolved.chatModel())
                        .defaultOptions(resolved.chatOptions())
                        .defaultAdvisors(
                                messageChatMemoryAdvisor,
                                retrievalAugmentationAdvisor,
                                simpleLoggerAdvisor)
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
                        .chatClientResponse()
                        .mapNotNull(
                                response -> {
                                    if (refsHolder.get() == null) {
                                        List<ReferenceResponse> refs =
                                                referenceExtractor.extract(response);
                                        if (!refs.isEmpty()) {
                                            refsHolder.set(refs);
                                        }
                                    }
                                    return chatSseConverter.tokenEvent(response);
                                });

        // 5. 종료 이벤트 (참조문서 + done)
        Flux<ServerSentEvent<String>> endEvents =
                Flux.defer(
                        () -> {
                            List<ServerSentEvent<String>> events = new ArrayList<>();
                            List<ReferenceResponse> refs = refsHolder.get();
                            ServerSentEvent<String> refEvent =
                                    refs != null ? chatSseConverter.referencesEvent(refs) : null;
                            if (refEvent != null) {
                                events.add(refEvent);
                            }
                            events.add(chatSseConverter.doneEvent());
                            return Flux.fromIterable(events);
                        });

        return tokenEvents
                .concatWith(endEvents)
                .onErrorResume(
                        e -> {
                            log.error("채팅 응답 생성 중 오류 발생", e);
                            return Flux.just(chatSseConverter.errorEvent("응답 생성 중 오류가 발생했습니다."));
                        });
    }

    private String buildFilterExpression(UUID projectId, ChatRequest request) {
        if (request.groupName() == null && request.categoryName() == null) {
            return "project_id == '" + projectId + "'";
        }

        List<Long> sourceIds = resolveSourceIds(projectId, request);
        if (sourceIds.isEmpty()) {
            log.warn(
                    "스코프 필터링 대상 문서가 없음: groupName={}, categoryName={}",
                    request.groupName(),
                    request.categoryName());
            return null;
        }

        String ids = sourceIds.stream().map(String::valueOf).collect(Collectors.joining(", "));

        String expression = "source_id in [" + ids + "]";
        log.debug("벡터스토어 필터 표현식: {}", expression);
        return expression;
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
        String template = readTemplate();
        return template.replace("{userSystemMessage}", userSystemMessage != null ? userSystemMessage : "");
    }

    private String readTemplate() {
        try {
            return systemMessageResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("시스템 메시지 템플릿 로드 실패", e);
        }
    }
}

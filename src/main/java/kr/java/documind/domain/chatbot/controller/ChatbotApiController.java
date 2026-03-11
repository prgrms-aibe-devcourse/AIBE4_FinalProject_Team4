package kr.java.documind.domain.chatbot.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.chatbot.model.dto.request.ChatRequest;
import kr.java.documind.domain.chatbot.service.ChatbotMetaService;
import kr.java.documind.domain.chatbot.service.ChatbotService;
import kr.java.documind.global.annotation.ProjectId;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/projects/{publicId}/chatbot")
@RequiredArgsConstructor
public class ChatbotApiController {

    private final ChatbotService chatbotService;
    private final ChatbotMetaService chatbotMetaService;

    @GetMapping("/scopes/groups")
    public ApiResponse<List<String>> getGroupNames(@ProjectId UUID projectId) {
        List<String> groupNames = chatbotMetaService.getGroupNames(projectId);
        return ApiResponse.success(groupNames);
    }

    @GetMapping("/scopes/categories")
    public ApiResponse<List<String>> getCategoryNames(@ProjectId UUID projectId) {
        List<String> categoryNames = chatbotMetaService.getCategoryNames(projectId);
        return ApiResponse.success(categoryNames);
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
        @AuthenticationPrincipal CustomUserDetails authMember,
        @RequestBody @Valid ChatRequest request) {
        String conversationId = authMember.getMemberId().toString();
        return chatbotService.chat(conversationId, request);
    }
}

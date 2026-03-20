package kr.java.documind.domain.patchnote.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kr.java.documind.domain.chatbot.infrastructure.ChatModelResolver;
import kr.java.documind.domain.chatbot.model.vo.ResolvedChatModel;
import kr.java.documind.domain.patchnote.model.dto.DocumentSummaryResult;
import kr.java.documind.global.util.PromptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentSummaryGenerator {

    private static final String PROMPT_FILENAME = "extract-document-summary.st";

    /** LLM에 전달하는 문서 본문의 최대 문자 수 (비용 및 컨텍스트 제한 고려) */
    private static final int CONTENT_CHAR_LIMIT = 3000;

    private final ChatModelResolver chatModelResolver;
    private final PromptUtil promptUtil;
    private final ObjectMapper objectMapper;

    public DocumentSummaryResult generate(
            String documentName,
            String documentGroupName,
            String category,
            List<String> contentChunks) {
        try {
            String content = buildContent(documentName, documentGroupName, category, contentChunks);
            String prompt = promptUtil.render(PROMPT_FILENAME, Map.of("content", content));

            ResolvedChatModel resolved = chatModelResolver.resolveForPatchNote(null);
            String response =
                    ChatClient.builder(resolved.chatModel())
                            .defaultOptions(resolved.chatOptions())
                            .build()
                            .prompt()
                            .user(prompt)
                            .call()
                            .content();

            return parseResponse(response, documentName, category);

        } catch (Exception e) {
            log.warn(
                    "[DocumentSummaryGenerator] LLM 요약 생성 실패, 기본값 사용 - documentName: {}",
                    documentName,
                    e);
            return fallback(documentName, category);
        }
    }

    private String buildContent(
            String documentName, String groupName, String category, List<String> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("문서명: ").append(documentName).append('\n');
        sb.append("그룹: ").append(groupName).append('\n');
        sb.append("분류: ").append(category).append('\n');
        sb.append("내용:\n");

        int remaining = CONTENT_CHAR_LIMIT;
        for (String chunk : chunks.stream().filter(Objects::nonNull).toList()) {
            if (remaining <= 0) break;
            String part = chunk.length() > remaining ? chunk.substring(0, remaining) : chunk;
            sb.append(part).append('\n');
            remaining -= part.length();
        }
        return sb.toString().trim();
    }

    private DocumentSummaryResult parseResponse(
            String response, String documentName, String fallbackCategory) {
        try {
            String cleaned = stripMarkdownCodeBlock(response.trim());
            JsonNode node = objectMapper.readTree(cleaned);

            String title = node.path("title").asText("").trim();
            String summary = node.path("summary").asText("").trim();
            String categoryFromLlm = node.path("category").asText("").trim().toUpperCase();
            // isUserFacing 미존재/파싱 불가 시 true(보수적 fallback)로 처리
            boolean affectsPlayer =
                    !node.has("isUserFacing") || node.path("isUserFacing").asBoolean(true);

            if (title.isBlank()) title = documentName;
            if (summary.isBlank()) summary = documentName;
            if (categoryFromLlm.isBlank())
                categoryFromLlm = (fallbackCategory != null) ? fallbackCategory.toUpperCase() : "";

            return new DocumentSummaryResult(title, summary, categoryFromLlm, affectsPlayer);

        } catch (Exception e) {
            log.warn(
                    "[DocumentSummaryGenerator] JSON 파싱 실패, 기본값 사용 - documentName: {}",
                    documentName,
                    e);
            return fallback(documentName, fallbackCategory);
        }
    }

    private String stripMarkdownCodeBlock(String text) {
        if (!text.startsWith("```")) return text;
        return text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
    }

    private DocumentSummaryResult fallback(String documentName, String category) {
        String safeCategory = (category != null) ? category.toUpperCase() : "";
        // LLM 실패 시 affectsPlayer=true로 보수적 처리 (reranking 시 누락 방지)
        return new DocumentSummaryResult(documentName, documentName, safeCategory, true);
    }
}

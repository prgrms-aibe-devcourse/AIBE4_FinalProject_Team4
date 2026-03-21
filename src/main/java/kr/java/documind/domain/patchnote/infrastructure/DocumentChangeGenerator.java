package kr.java.documind.domain.patchnote.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import kr.java.documind.domain.chatbot.infrastructure.ChatModelResolver;
import kr.java.documind.domain.chatbot.model.vo.ResolvedChatModel;
import kr.java.documind.domain.patchnote.model.dto.DocumentSummaryResult;
import kr.java.documind.domain.patchnote.model.dto.PatchCandidate;
import kr.java.documind.global.util.PromptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentChangeGenerator {

    private static final String PROMPT_FILENAME = "document-change-summary.st";

    /** LLM에 전달하는 변경 내용의 최대 문자 수 (이전/현재 각각 적용) */
    private static final int CONTENT_CHAR_LIMIT = 2000;

    private final ChatModelResolver chatModelResolver;
    private final PromptUtil promptUtil;
    private final ObjectMapper objectMapper;

    public DocumentSummaryResult generate(
            PatchCandidate candidate,
            String documentName,
            String documentGroupName,
            String category) {
        try {
            String previousContent = truncate(candidate.previousContent());
            String currentContent = truncate(candidate.currentContent());

            String prompt =
                    promptUtil.render(
                            PROMPT_FILENAME,
                            Map.of(
                                    "changeType",
                                    candidate.changeType(),
                                    "previousContent",
                                    previousContent != null ? previousContent : "(없음)",
                                    "currentContent",
                                    currentContent != null ? currentContent : "(없음)"));

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
                    "[DocumentChangeGenerator] LLM 변경 요약 생성 실패, 기본값 사용 - documentName: {}, changeType: {}",
                    documentName,
                    candidate.changeType(),
                    e);
            return fallback(documentName, candidate.changeType(), category);
        }
    }

    private String truncate(String text) {
        if (text == null) return null;
        return text.length() > CONTENT_CHAR_LIMIT ? text.substring(0, CONTENT_CHAR_LIMIT) : text;
    }

    private DocumentSummaryResult parseResponse(
            String response, String documentName, String fallbackCategory) {
        try {
            String cleaned = stripMarkdownCodeBlock(response.trim());
            JsonNode node = objectMapper.readTree(cleaned);

            String title = node.path("title").asText("").trim();
            String summary = node.path("summary").asText("").trim();
            String categoryFromLlm = node.path("category").asText("").trim().toUpperCase();

            // 보수적 처리: 명시적으로 true일 때만 true
            boolean affectsPlayer =
                    node.has("isUserFacing") && node.path("isUserFacing").asBoolean(false);

            if (title.isBlank()) title = documentName;
            if (summary.isBlank()) summary = documentName;
            if (categoryFromLlm.isBlank()) {
                categoryFromLlm = (fallbackCategory != null) ? fallbackCategory.toUpperCase() : "";
            }

            return new DocumentSummaryResult(title, summary, categoryFromLlm, affectsPlayer);

        } catch (Exception e) {
            log.warn(
                    "[DocumentChangeGenerator] JSON 파싱 실패, 기본값 사용 - documentName: {}",
                    documentName,
                    e);
            return fallback(documentName, "", fallbackCategory);
        }
    }

    private String stripMarkdownCodeBlock(String text) {
        if (!text.startsWith("```")) return text;
        return text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
    }

    private DocumentSummaryResult fallback(
            String documentName, String changeType, String category) {
        String safeCategory = (category != null) ? category.toUpperCase() : "";
        String title =
                (changeType != null && !changeType.isBlank())
                        ? changeType + " 변경: " + documentName
                        : documentName;

        // 보수적 fallback: 플레이어 노출 여부는 false
        return new DocumentSummaryResult(title, title, safeCategory, false);
    }
}

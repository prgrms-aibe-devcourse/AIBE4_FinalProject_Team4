package kr.java.documind.domain.patchnote.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import kr.java.documind.domain.chatbot.infrastructure.ChatModelResolver;
import kr.java.documind.domain.chatbot.model.vo.ResolvedChatModel;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.patchnote.model.dto.IssueSummaryResult;
import kr.java.documind.global.util.PromptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IssueSummaryGenerator {

    private static final String PROMPT_FILENAME = "extract-issue-summary.st";

    private static final int FIELD_MAX_LENGTH = 300;

    private final ChatModelResolver chatModelResolver;
    private final PromptUtil promptUtil;
    private final ObjectMapper objectMapper;

    public IssueSummaryResult generate(Issue issue) {
        try {
            String content = buildContent(issue);
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

            return parseResponse(response, issue);

        } catch (Exception e) {
            log.warn(
                    "[IssueSummaryGenerator] LLM 요약 생성 실패, 기본 요약(제목) 사용 - issueId: {}",
                    issue.getId(),
                    e);
            return fallback(issue);
        }
    }

    private String buildContent(Issue issue) {
        StringBuilder sb = new StringBuilder();

        sb.append("제목: ").append(issue.getTitle()).append('\n');

        if (issue.getSeverity() != null) {
            sb.append("심각도: ").append(issue.getSeverity().getValue()).append('\n');
        }

        if (hasText(issue.getDescription())) {
            sb.append("설명: ")
                    .append(truncate(issue.getDescription(), FIELD_MAX_LENGTH))
                    .append('\n');
        }

        if (hasText(issue.getResolutionNote())) {
            sb.append("해결 방법: ")
                    .append(truncate(issue.getResolutionNote(), FIELD_MAX_LENGTH))
                    .append('\n');
        }

        if (issue.getIssueType() != null) {
            sb.append("이슈 유형: ").append(issue.getIssueType().getDescription()).append('\n');
        }

        return sb.toString().trim();
    }

    private IssueSummaryResult parseResponse(String response, Issue issue) {
        try {
            String cleaned = stripMarkdownCodeBlock(response.trim());
            JsonNode node = objectMapper.readTree(cleaned);

            String title = node.path("title").asText("").trim();
            String summary = node.path("summary").asText("").trim();

            // LLM이 빈 값을 반환한 경우 fallback
            if (title.isBlank()) title = issue.getTitle();
            if (summary.isBlank()) summary = issue.getTitle();

            return new IssueSummaryResult(title, summary);

        } catch (Exception e) {
            log.warn(
                    "[IssueSummaryGenerator] JSON 파싱 실패, 기본 요약 사용 - issueId: {}", issue.getId(), e);
            return fallback(issue);
        }
    }

    private String stripMarkdownCodeBlock(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        return text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
    }

    private IssueSummaryResult fallback(Issue issue) {
        return new IssueSummaryResult(issue.getTitle(), issue.getTitle());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}

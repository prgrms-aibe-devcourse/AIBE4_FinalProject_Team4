package kr.java.documind.domain.patchnote.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import kr.java.documind.domain.patchnote.model.dto.ItemContext;
import kr.java.documind.domain.patchnote.model.dto.RagContext;
import kr.java.documind.domain.patchnote.model.dto.RagEvidence;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PatchNotePromptService {

    private final String promptTemplate;

    public PatchNotePromptService(
            @Value("classpath:prompts/patch-note-draft.st") Resource promptResource) {
        try {
            this.promptTemplate =
                    new String(promptResource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("패치노트 초안 프롬프트 템플릿 로드 실패", e);
        }
    }

    public String buildSystemPrompt(RagContext ragContext) {
        String context = renderItemContexts(ragContext.itemContexts());
        return promptTemplate.replace("{context}", context);
    }

    public String buildUserPrompt(
            int majorVersion, int minorVersion, int patchVersion, String additionalPrompt) {
        String base =
                "v%d.%d.%d 패치노트 초안을 작성해 주세요.".formatted(majorVersion, minorVersion, patchVersion);
        if (additionalPrompt == null || additionalPrompt.isBlank()) {
            return base;
        }
        return base
                + "\n\n추가 지침 (최우선 반영):\n"
                + additionalPrompt.strip()
                + "\n\n본문은 기계적인 목록이 아니라 실제 서비스 패치노트처럼 자연스럽게 작성해 주세요.";
    }

    public String buildUserPrompt(int majorVersion, int minorVersion, int patchVersion) {
        return buildUserPrompt(majorVersion, minorVersion, patchVersion, null);
    }

    private String renderItemContexts(List<ItemContext> itemContexts) {
        if (itemContexts.isEmpty()) {
            return "소스 정보가 없습니다.";
        }

        StringBuilder sb = new StringBuilder();

        for (ItemContext item : itemContexts) {
            sb.append("### ITEM: ")
                    .append(item.ref())
                    .append(" [")
                    .append(item.patchType().name())
                    .append("]\n");

            sb.append("대표 제목: ").append(item.title()).append('\n');
            sb.append("참고 요약(그대로 줄바꿈 리스트처럼 옮기지 말고, 하나의 자연스러운 문장 흐름으로 재구성해요): ")
                    .append(item.summary())
                    .append('\n');

            if (!item.evidences().isEmpty()) {
                sb.append('\n');
                sb.append("아래 근거들을 참고하여, 여러 변경을 필요하면 하나의 흐름으로 통합해서 작성해요.\n");
                for (RagEvidence ev : item.evidences()) {
                    sb.append(renderEvidenceHeader(ev)).append('\n');
                    sb.append(ev.text()).append('\n');
                }
            }

            sb.append("---\n");
        }

        return sb.toString().strip();
    }

    private String renderEvidenceHeader(RagEvidence ev) {
        StringBuilder header = new StringBuilder("근거 [역할: ").append(ev.role()).append(']');
        if (ev.playerVisible()) {
            header.append(" [플레이어영향]");
        }
        if (ev.numericChange()) {
            header.append(" [수치변경]");
        }
        header.append(':');
        return header.toString();
    }
}

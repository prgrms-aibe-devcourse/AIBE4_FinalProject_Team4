package kr.java.documind.domain.patchnote.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import kr.java.documind.domain.patchnote.model.dto.RagContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * 패치노트 초안 생성용 LLM 프롬프트 빌더.
 *
 * <p>시스템 프롬프트는 {@code classpath:prompts/patch-note-draft.st} 템플릿을 기반으로
 * RAG 컨텍스트를 삽입하여 구성한다. 유저 프롬프트는 목표 버전 정보를 담는다.
 */
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

    /**
     * 시스템 프롬프트 생성.
     *
     * <p>RAG 컨텍스트가 비어 있는 경우 소스 정보 없이 기본 지침만 포함된 프롬프트를 반환한다.
     *
     * @param ragContext 소스 컨텍스트
     * @return 완성된 시스템 프롬프트 문자열
     */
    public String buildSystemPrompt(RagContext ragContext) {
        String context =
                ragContext.contextText().isBlank() ? "소스 정보가 없습니다." : ragContext.contextText();
        return promptTemplate.replace("{context}", context);
    }

    /**
     * 유저 프롬프트 생성.
     *
     * @param majorVersion     대상 버전 major
     * @param minorVersion     대상 버전 minor
     * @param patchVersion     대상 버전 patch
     * @param additionalPrompt 추가 지침 (null 또는 공백이면 무시)
     * @return 유저 프롬프트 문자열
     */
    public String buildUserPrompt(
            int majorVersion, int minorVersion, int patchVersion, String additionalPrompt) {
        String base =
                "v%d.%d.%d 패치노트 초안을 작성해 주세요.".formatted(majorVersion, minorVersion, patchVersion);
        if (additionalPrompt == null || additionalPrompt.isBlank()) {
            return base;
        }
        return base + "\n\n추가 지침:\n" + additionalPrompt.strip();
    }

    /**
     * 유저 프롬프트 생성 (추가 지침 없음).
     *
     * @param majorVersion 대상 버전 major
     * @param minorVersion 대상 버전 minor
     * @param patchVersion 대상 버전 patch
     * @return 유저 프롬프트 문자열
     */
    public String buildUserPrompt(int majorVersion, int minorVersion, int patchVersion) {
        return buildUserPrompt(majorVersion, minorVersion, patchVersion, null);
    }
}

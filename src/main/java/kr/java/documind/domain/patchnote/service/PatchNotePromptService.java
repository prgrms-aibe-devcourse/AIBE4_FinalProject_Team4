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

/**
 * 패치노트 초안 생성용 LLM 프롬프트 빌더.
 *
 * <h3>시스템 프롬프트</h3>
 *
 * {@code classpath:prompts/patch-note-draft.st} 템플릿을 기반으로, {@link RagContext}가 보유한 {@link
 * ItemContext} 목록을 구조화된 증거 블록 텍스트로 렌더링하여 {@code {context}} 자리에 삽입한다. 단일 산문형 {@code contextText}를 직접
 * 주입하던 이전 방식과 달리, 항목(ref) 단위로 제목·요약·증거 블록을 분리하여 LLM이 개별 항목을 명확하게 인식하도록 구성한다.
 *
 * <h3>유저 프롬프트</h3>
 *
 * 목표 버전 정보와 선택적 추가 지침을 담는다. 추가 지침({@code additionalPrompt})은 가장 높은 우선순위로 처리되어야 함을 프롬프트 템플릿이 명시한다.
 *
 * <h3>모델 출력 계약</h3>
 *
 * 프롬프트 템플릿은 모델에게 {@code PatchNoteDraftResponse} 스키마를 따르는 JSON만 반환하도록 지시한다. 인라인 소스 태그({@code
 * {{source:N}}}) 대신 {@code sourceRefs} 배열로 참조를 구조화한다.
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
     * <p>{@link RagContext#itemContexts()}가 비어 있는 경우 소스 정보 없음 메시지를 삽입한다. 그렇지 않으면 각 {@link
     * ItemContext}를 구조화된 증거 블록 형식으로 렌더링하여 {@code {context}} 자리에 삽입한다.
     *
     * @param ragContext 소스 컨텍스트
     * @return 완성된 시스템 프롬프트 문자열
     */
    public String buildSystemPrompt(RagContext ragContext) {
        String context = renderItemContexts(ragContext.itemContexts());
        return promptTemplate.replace("{context}", context);
    }

    /**
     * 유저 프롬프트 생성.
     *
     * <p>{@code additionalPrompt}는 가장 높은 우선순위로 처리된다. 시스템 프롬프트 템플릿도 이를 명시한다.
     *
     * @param majorVersion 대상 버전 major
     * @param minorVersion 대상 버전 minor
     * @param patchVersion 대상 버전 patch
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
        return base + "\n\n추가 지침 (최우선 반영):\n" + additionalPrompt.strip();
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

    // ─────────────────────────────────────────────────────────────────────────
    // 증거 블록 렌더러
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@link ItemContext} 목록을 LLM 시스템 프롬프트에 삽입할 구조화된 증거 블록 텍스트로 렌더링한다.
     *
     * <p>렌더링 형식 (예시):
     *
     * <pre>
     * ### ITEM: ISSUE-42 [FIX]
     * 제목: 결제 버튼 무반응 버그
     * 요약: iOS 기기에서 결제 버튼이 동작하지 않는 문제
     *
     * 증거 [역할: resolution] [플레이어영향] [수치변경]:
     * iOS 16.4 이상 기기에서 결제 버튼 터치 이벤트가 무시되는 문제를 수정하였습니다.
     *
     * 증거 [역할: background]:
     * 결제 모듈 v2.3.1에서 UIKit 이벤트 위임 방식 변경으로 인해 발생함.
     * ---
     * </pre>
     *
     * @param itemContexts 항목별 컨텍스트 목록
     * @return 렌더링된 증거 블록 텍스트 (빈 목록이면 "소스 정보가 없습니다." 반환)
     */
    private String renderItemContexts(List<ItemContext> itemContexts) {
        if (itemContexts.isEmpty()) {
            return "소스 정보가 없습니다.";
        }

        StringBuilder sb = new StringBuilder();

        for (ItemContext item : itemContexts) {
            // 항목 헤더: REF와 PatchType
            sb.append("### ITEM: ")
                    .append(item.ref())
                    .append(" [")
                    .append(item.patchType().name())
                    .append("]\n");

            // 제목 · 요약
            sb.append("제목: ").append(item.title()).append('\n');
            sb.append("요약: ").append(item.summary()).append('\n');

            // 증거 블록 목록
            if (!item.evidences().isEmpty()) {
                sb.append('\n');
                for (RagEvidence ev : item.evidences()) {
                    sb.append(renderEvidenceHeader(ev)).append('\n');
                    sb.append(ev.text()).append('\n');
                }
            }

            sb.append("---\n");
        }

        return sb.toString().strip();
    }

    /**
     * 단일 {@link RagEvidence}의 헤더 줄을 렌더링한다.
     *
     * <p>역할과 메타데이터 플래그를 괄호로 표기한다. 예: {@code 증거 [역할: resolution] [플레이어영향] [수치변경]:}
     */
    private String renderEvidenceHeader(RagEvidence ev) {
        StringBuilder header = new StringBuilder("증거 [역할: ").append(ev.role()).append(']');
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

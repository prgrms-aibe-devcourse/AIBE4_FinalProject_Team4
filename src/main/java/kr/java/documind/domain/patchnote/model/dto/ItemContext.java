package kr.java.documind.domain.patchnote.model.dto;

import java.util.List;
import kr.java.documind.domain.patchnote.model.enums.PatchType;

/**
 * 패치노트 초안 생성을 위한 단일 PendingItem의 컨텍스트 블록.
 *
 * <p>하나의 {@code PendingItem}에 대한 모든 RAG 증거를 구조화하여 보유한다.
 * {@link PatchNotePromptService}가 이 목록으로부터 LLM에 전달할 증거 블록 텍스트를 렌더링한다.
 *
 * <p>{@code allowedSourceRefs}는 LLM이 이 항목의 응답에서 {@code sourceRefs}로 인용할 수 있는
 * REF 목록이다. 현재는 항목 자신의 ref만 허용하며, 추후 관련 소스 cross-ref 확장 시 사용한다.
 *
 * @param ref              이 항목의 고유 소스 REF (예: {@code "ISSUE-42"}, {@code "DOC-17-0"})
 * @param patchType        변경 분류 (NEW / CHANGE / FIX / MAINTENANCE)
 * @param title            항목 제목
 * @param summary          항목 요약 (LLM 생성)
 * @param evidences        이 항목에 연결된 증거 블록 목록 (score 내림차순)
 * @param allowedSourceRefs 이 항목에서 출처로 인용할 수 있는 REF 목록
 */
public record ItemContext(
        String ref,
        PatchType patchType,
        String title,
        String summary,
        List<RagEvidence> evidences,
        List<String> allowedSourceRefs) {}

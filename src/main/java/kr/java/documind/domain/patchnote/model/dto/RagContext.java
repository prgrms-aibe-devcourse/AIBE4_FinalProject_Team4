package kr.java.documind.domain.patchnote.model.dto;

import java.util.List;
import java.util.Map;

/**
 * 패치노트 초안 생성을 위한 RAG 컨텍스트.
 *
 * <p>이전의 단일 {@code contextText} 문자열 대신, 항목별로 구조화된 {@link ItemContext} 목록을 보유한다. {@link
 * kr.java.documind.domain.patchnote.service.PatchNotePromptService}가 이 목록으로부터 LLM 시스템 프롬프트의 증거 블록
 * 텍스트를 렌더링한다.
 *
 * <p>소스 REF 맵({@code sourceRefMap})과 참조 목록({@code sourceRefs})은 LLM 응답 검증 및 SSE sources 이벤트 전송에 계속
 * 사용된다.
 *
 * @param itemContexts 항목별 구조화 컨텍스트 목록 (PatchType 그룹 순서 보장)
 * @param sourceRefMap REF → 제목 매핑 (예: {@code "ISSUE-245"} → {@code "결제 오류 패치"})
 * @param sourceRefs 소스 REF 목록 (순서 보장, 중복 제거)
 * @param tokenEstimation 사전 토큰 추정 결과
 */
public record RagContext(
        List<ItemContext> itemContexts,
        Map<String, String> sourceRefMap,
        List<String> sourceRefs,
        TokenEstimation tokenEstimation) {

    /** 빈 컨텍스트 — 활성 소스 없을 때 사용. */
    public static RagContext empty(TokenEstimation tokenEstimation) {
        return new RagContext(List.of(), Map.of(), List.of(), tokenEstimation);
    }
}

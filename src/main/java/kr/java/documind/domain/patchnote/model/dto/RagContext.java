package kr.java.documind.domain.patchnote.model.dto;

import java.util.List;
import java.util.Map;

/**
 * 패치노트 초안 생성을 위한 RAG 컨텍스트.
 *
 * @param contextText      LLM 시스템 프롬프트에 삽입할 소스 컨텍스트 텍스트
 * @param sourceRefMap     REF → 제목 매핑 (예: "ISSUE-245" → "결제 오류 패치")
 * @param sourceRefs       소스 REF 목록 (순서 보장, 중복 제거)
 * @param tokenEstimation  사전 토큰 추정 결과
 */
public record RagContext(
        String contextText,
        Map<String, String> sourceRefMap,
        List<String> sourceRefs,
        TokenEstimation tokenEstimation) {

    /** 빈 컨텍스트 — 활성 소스 없을 때 사용. */
    public static RagContext empty(TokenEstimation tokenEstimation) {
        return new RagContext("", Map.of(), List.of(), tokenEstimation);
    }
}

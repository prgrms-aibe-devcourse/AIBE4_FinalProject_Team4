package kr.java.documind.domain.patchnote.model.dto;

import java.util.List;

/**
 * SSE {@code done} 이벤트 페이로드.
 *
 * <p>스트리밍 완료 후 최종 정제된 컨텐츠와 소스 REF 목록을 포함한다.
 *
 * @param cleanedContent {@code {{source:REF}}} 태그가 제거된 최종 패치노트 텍스트
 * @param sourceRefs     LLM 응답에서 파싱된 소스 REF 목록 (예: ["ISSUE-245", "DOC-1024"])
 */
public record DraftResult(String cleanedContent, List<String> sourceRefs) {}

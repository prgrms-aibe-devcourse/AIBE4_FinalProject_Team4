package kr.java.documind.domain.patchnote.model.dto;

import java.util.List;

/**
 * SSE {@code done} 이벤트 페이로드.
 *
 * <p>스트리밍 완료 후 최종 컨텐츠와 소스 REF 목록을 포함한다.
 *
 * @param cleanedContent {@link kr.java.documind.domain.patchnote.service.PatchNoteRenderer}가 렌더링한
 *     마크다운 텍스트. {@code {{source:REF}}} 인라인 태그가 포함되어 있으며, 프론트엔드에서 링크로 치환하거나 제거하여 표시한다.
 * @param sourceRefs LLM 응답에서 파싱된 소스 REF 목록, 등장 순서 보장 (예: ["ISSUE-245", "DOC-1024"])
 */
public record DraftResult(String cleanedContent, List<String> sourceRefs) {}

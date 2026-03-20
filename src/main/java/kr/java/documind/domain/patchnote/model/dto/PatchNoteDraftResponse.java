package kr.java.documind.domain.patchnote.model.dto;

import java.util.List;

/**
 * LLM이 반환하는 패치노트 초안의 최상위 구조화 응답.
 *
 * <p>모든 LLM 출력은 이 스키마를 따르는 JSON 문자열이어야 한다. {@code PatchNoteDraftService}가 LLM 응답을 파싱하여 이 DTO로
 * 역직렬화한다.
 *
 * <p>섹션 순서는 항상 {@code NEW → CHANGE → FIX → MAINTENANCE}이며, 해당 분류의 항목이 없으면 그 섹션은 {@code sections}
 * 배열에 포함되지 않는다.
 *
 * <p>마크다운 렌더링은 {@link kr.java.documind.domain.patchnote.service.PatchNoteRenderer}가 담당한다.
 *
 * @param preamble 도입 텍스트 (선택, null 허용) — 사용자 템플릿에서 인삿말 등 섹션 앞 도입부
 * @param sections 섹션 목록 (분류별로 그룹화된 패치노트 항목)
 * @param postamble 마무리 텍스트 (선택, null 허용) — 사용자 템플릿에서 맺음말 등 섹션 뒤 결론부
 */
public record PatchNoteDraftResponse(
        String preamble, List<PatchNoteSectionResponse> sections, String postamble) {}

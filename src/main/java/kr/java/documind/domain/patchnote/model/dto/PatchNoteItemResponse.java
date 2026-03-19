package kr.java.documind.domain.patchnote.model.dto;

import java.util.List;

/**
 * 패치노트 초안 응답 — 단일 항목.
 *
 * <p>LLM이 반환하는 JSON 구조의 최하위 단위. 하나의 불릿포인트 항목을 나타낸다. 소스 참조는 인라인 태그가 아닌 {@code sourceRefs} 배열로 구조화된다.
 *
 * <p>서버는 이 DTO를 수신한 뒤 {@code sourceRefs}를 검증하고, 최종 렌더링 시 필요한 링크/태그 형식으로 변환한다.
 *
 * @param text 플레이어에게 전달할 항목 내용 (해요체, 1~2문장)
 * @param sourceRefs 이 항목의 근거가 된 소스 REF 목록 (예: {@code ["ISSUE-42", "DOC-17-0"]})
 */
public record PatchNoteItemResponse(String text, List<String> sourceRefs) {}

package kr.java.documind.domain.patchnote.model.dto;

import java.util.List;

/**
 * 패치노트 초안 응답 — 단일 섹션.
 *
 * <p>LLM이 반환하는 JSON 구조의 중간 단위. 하나의 변경 분류(NEW / CHANGE / FIX / MAINTENANCE)에
 * 속하는 항목 목록을 보유한다. 해당 분류의 항목이 없으면 LLM은 이 섹션 자체를 생략한다.
 *
 * @param sectionType 변경 분류 문자열 ({@code "NEW"} / {@code "CHANGE"} / {@code "FIX"} / {@code "MAINTENANCE"})
 * @param items       이 섹션에 속하는 패치노트 항목 목록
 */
public record PatchNoteSectionResponse(String sectionType, List<PatchNoteItemResponse> items) {}

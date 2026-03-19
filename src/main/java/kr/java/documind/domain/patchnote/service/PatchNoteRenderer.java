package kr.java.documind.domain.patchnote.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import kr.java.documind.domain.patchnote.model.dto.DraftResult;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteDraftResponse;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteItemResponse;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteSectionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 검증된 {@link PatchNoteDraftResponse}를 최종 마크다운 패치노트로 렌더링하는 서비스.
 *
 * <h3>렌더링 책임</h3>
 * <ul>
 *   <li>섹션 순서 강제: {@code NEW → CHANGE → FIX → MAINTENANCE} (모델 출력 순서 무시)
 *   <li>비어 있는 섹션 제외
 *   <li>H2 섹션 헤더 렌더링 (한국어 레이블)
 *   <li>불릿포인트 항목 렌더링
 *   <li>검증된 소스 REF를 {@code {{source:REF}}} 인라인 태그로 삽입
 *   <li>사용된 REF 목록을 등장 순서로 수집
 * </ul>
 *
 * <h3>설계 원칙</h3>
 * 모델은 의미적 내용만 생성하고, 표시 형식(헤더, 태그, 순서)은 서버가 완전히 제어한다.
 * 렌더링 결과는 {@link DraftResult}로 반환하며, 프론트엔드는 마크다운 및 소스 REF 목록을 수신한다.
 */
@Slf4j
@Service
public class PatchNoteRenderer {

    /**
     * 섹션 정렬 기준 순서.
     *
     * <p>LLM이 반환한 섹션 순서를 무시하고 이 순서대로 재배열한다.
     */
    private static final List<String> CANONICAL_ORDER =
            List.of("NEW", "CHANGE", "FIX", "MAINTENANCE");

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 검증된 응답을 최종 마크다운 텍스트로 렌더링한다.
     *
     * <p>섹션이 없거나 모든 섹션이 비어 있으면 빈 {@link DraftResult}를 반환한다.
     *
     * @param response 소스 REF 검증이 완료된 응답 DTO
     * @return 렌더링된 마크다운과 사용된 REF 목록을 담은 최종 결과
     */
    public DraftResult render(PatchNoteDraftResponse response) {
        if (response.sections() == null || response.sections().isEmpty()) {
            log.warn("렌더링 대상 섹션 없음 — 빈 DraftResult 반환");
            return new DraftResult("", List.of());
        }

        // 정규 순서로 섹션 재배열; 알 수 없는 섹션 타입은 뒤에 추가
        List<PatchNoteSectionResponse> ordered = orderSections(response.sections());

        StringBuilder sb      = new StringBuilder();
        List<String>  usedRefs = new ArrayList<>();

        // 도입 텍스트 (preamble): 사용자 템플릿의 인삿말 등
        if (response.preamble() != null && !response.preamble().isBlank()) {
            sb.append(response.preamble().strip()).append("\n\n");
        }

        for (PatchNoteSectionResponse section : ordered) {
            renderSection(section, sb, usedRefs);
        }

        // 마무리 텍스트 (postamble): 사용자 템플릿의 맺음말 등
        if (response.postamble() != null && !response.postamble().isBlank()) {
            sb.append('\n').append(response.postamble().strip());
        }

        String markdown = sb.toString().strip();

        log.debug("패치노트 렌더링 완료 — 섹션: {}개, REF: {}개, 글자 수: {}",
                ordered.size(), usedRefs.size(), markdown.length());

        return new DraftResult(markdown, List.copyOf(usedRefs));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 내부 렌더링
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 단일 섹션을 {@link StringBuilder}에 렌더링한다.
     *
     * <p>비어 있는 항목 목록을 가진 섹션은 출력되지 않는다.
     */
    private void renderSection(
            PatchNoteSectionResponse section,
            StringBuilder sb,
            List<String> usedRefs) {

        if (section.items() == null || section.items().isEmpty()) {
            return;
        }

        sb.append("## ").append(sectionLabel(section.sectionType())).append('\n');

        for (PatchNoteItemResponse item : section.items()) {
            renderItem(item, sb, usedRefs);
        }

        sb.append('\n');
    }

    /**
     * 단일 항목을 불릿포인트 형식으로 렌더링한다.
     *
     * <p>검증된 {@code sourceRefs}가 존재하면 항목 뒤에 {@code {{source:REF}}} 태그를 공백으로 이어 붙인다.
     * 태그 삽입 순서는 {@code sourceRefs} 배열 순서를 따른다.
     */
    private void renderItem(
            PatchNoteItemResponse item,
            StringBuilder sb,
            List<String> usedRefs) {

        if (item.text() == null || item.text().isBlank()) {
            return;
        }

        sb.append("- ").append(item.text().strip());

        if (item.sourceRefs() != null && !item.sourceRefs().isEmpty()) {
            String tags = item.sourceRefs().stream()
                    .peek(ref -> { if (!usedRefs.contains(ref)) usedRefs.add(ref); })
                    .map(ref -> "{{source:" + ref + "}}")
                    .collect(Collectors.joining(" "));
            sb.append(' ').append(tags);
        }

        sb.append('\n');
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 섹션 정렬
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 섹션을 {@link #CANONICAL_ORDER}에 따라 재배열한다.
     *
     * <p>정규 순서에 없는 sectionType(예: 모델이 임의로 생성한 섹션 이름)은
     * 정규 섹션 뒤에 원래 순서대로 추가된다.
     */
    private List<PatchNoteSectionResponse> orderSections(
            List<PatchNoteSectionResponse> sections) {

        List<PatchNoteSectionResponse> ordered = new ArrayList<>();

        // 1. 정규 순서로 배치
        for (String type : CANONICAL_ORDER) {
            sections.stream()
                    .filter(s -> type.equalsIgnoreCase(s.sectionType()))
                    .filter(s -> s.items() != null && !s.items().isEmpty())
                    .findFirst()
                    .ifPresent(ordered::add);
        }

        // 2. 정규 목록에 없는 섹션 추가 (예: 모델이 반환한 알 수 없는 sectionType)
        for (PatchNoteSectionResponse section : sections) {
            boolean isCanonical = CANONICAL_ORDER.stream()
                    .anyMatch(type -> type.equalsIgnoreCase(section.sectionType()));
            if (!isCanonical && section.items() != null && !section.items().isEmpty()) {
                ordered.add(section);
                log.debug("비표준 섹션 타입 포함 — sectionType: [{}]", section.sectionType());
            }
        }

        return ordered;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 섹션 레이블
    // ─────────────────────────────────────────────────────────────────────────

    private String sectionLabel(String sectionType) {
        if (sectionType == null) return "";
        return switch (sectionType.toUpperCase()) {
            case "NEW"         -> "신규";
            case "CHANGE"      -> "변경";
            case "FIX"         -> "수정";
            case "MAINTENANCE" -> "유지보수";
            default            -> sectionType;
        };
    }
}

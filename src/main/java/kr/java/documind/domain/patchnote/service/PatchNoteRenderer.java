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

@Slf4j
@Service
public class PatchNoteRenderer {

    private static final List<String> CANONICAL_ORDER =
            List.of("NEW", "CHANGE", "FIX", "MAINTENANCE");

    public DraftResult render(PatchNoteDraftResponse response) {
        if (response.sections() == null || response.sections().isEmpty()) {
            log.warn("렌더링 대상 섹션 없음 — 빈 DraftResult 반환");
            return new DraftResult("", List.of());
        }

        // 정규 순서로 섹션 재배열; 알 수 없는 섹션 타입은 뒤에 추가
        List<PatchNoteSectionResponse> ordered = orderSections(response.sections());

        StringBuilder sb = new StringBuilder();
        List<String> usedRefs = new ArrayList<>();

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

        log.debug(
                "패치노트 렌더링 완료 — 섹션: {}개, REF: {}개, 글자 수: {}",
                ordered.size(),
                usedRefs.size(),
                markdown.length());

        return new DraftResult(markdown, List.copyOf(usedRefs));
    }

    private void renderSection(
            PatchNoteSectionResponse section, StringBuilder sb, List<String> usedRefs) {

        if (section.items() == null || section.items().isEmpty()) {
            return;
        }

        sb.append("## ").append(sectionLabel(section.sectionType())).append('\n');

        for (PatchNoteItemResponse item : section.items()) {
            renderItem(item, sb, usedRefs);
        }

        sb.append('\n');
    }

    private void renderItem(PatchNoteItemResponse item, StringBuilder sb, List<String> usedRefs) {

        if (item.text() == null || item.text().isBlank()) {
            return;
        }

        sb.append("- ").append(item.text().strip());

        if (item.sourceRefs() != null && !item.sourceRefs().isEmpty()) {
            String tags =
                    item.sourceRefs().stream()
                            .peek(
                                    ref -> {
                                        if (!usedRefs.contains(ref)) usedRefs.add(ref);
                                    })
                            .map(ref -> "{{source:" + ref + "}}")
                            .collect(Collectors.joining(" "));
            sb.append(' ').append(tags);
        }

        sb.append('\n');
    }

    private List<PatchNoteSectionResponse> orderSections(List<PatchNoteSectionResponse> sections) {

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
            boolean isCanonical =
                    CANONICAL_ORDER.stream()
                            .anyMatch(type -> type.equalsIgnoreCase(section.sectionType()));
            if (!isCanonical && section.items() != null && !section.items().isEmpty()) {
                ordered.add(section);
                log.debug("비표준 섹션 타입 포함 — sectionType: [{}]", section.sectionType());
            }
        }

        return ordered;
    }

    private String sectionLabel(String sectionType) {
        if (sectionType == null) return "";
        return switch (sectionType.toUpperCase()) {
            case "NEW" -> "신규";
            case "CHANGE" -> "변경";
            case "FIX" -> "수정";
            case "MAINTENANCE" -> "유지보수";
            default -> sectionType;
        };
    }
}

package kr.java.documind.domain.patchnote.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteDraftResponse;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteItemResponse;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteSectionResponse;
import kr.java.documind.domain.patchnote.model.dto.RagContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PatchNoteRefValidator {

    public PatchNoteDraftResponse validate(PatchNoteDraftResponse response, RagContext ragContext) {
        if (response.sections() == null || response.sections().isEmpty()) {
            return response;
        }

        Set<String> whitelist = Set.copyOf(ragContext.sourceRefs());

        int originalItems = countItems(response);
        int originalSections = response.sections().size();

        List<PatchNoteSectionResponse> validSections =
                response.sections().stream()
                        .map(section -> validateSection(section, whitelist))
                        .filter(section -> section.items() != null && !section.items().isEmpty())
                        .toList();

        int validItems =
                validSections.stream()
                        .filter(s -> s.items() != null)
                        .mapToInt(s -> s.items().size())
                        .sum();
        int droppedItems = originalItems - validItems;
        int droppedSections = originalSections - validSections.size();

        if (droppedItems > 0 || droppedSections > 0) {
            log.info(
                    "패치노트 REF 검증 완료 — 항목 {}/{}개 유지, {}개 드롭, 섹션 {}/{}개 유지",
                    validItems,
                    originalItems,
                    droppedItems,
                    validSections.size(),
                    originalSections);
        } else {
            log.debug("패치노트 REF 검증 완료 — 모든 항목 유효 ({}개)", validItems);
        }

        return new PatchNoteDraftResponse(response.preamble(), validSections, response.postamble());
    }

    private PatchNoteSectionResponse validateSection(
            PatchNoteSectionResponse section, Set<String> whitelist) {

        if (section.items() == null) {
            return new PatchNoteSectionResponse(section.sectionType(), List.of());
        }

        List<PatchNoteItemResponse> validItems =
                section.items().stream()
                        .filter(item -> item.text() != null && !item.text().isBlank())
                        .map(item -> stripHallucinatedRefs(item, whitelist))
                        .toList();

        return new PatchNoteSectionResponse(section.sectionType(), validItems);
    }

    private PatchNoteItemResponse stripHallucinatedRefs(
            PatchNoteItemResponse item, Set<String> whitelist) {

        if (item.sourceRefs() == null || item.sourceRefs().isEmpty()) {
            return item;
        }

        List<String> validRefs =
                item.sourceRefs().stream()
                        .filter(
                                ref -> {
                                    if (whitelist.contains(ref)) {
                                        return true;
                                    }
                                    log.warn("환각 REF 제거 — ref: [{}] (화이트리스트에 없음)", ref);
                                    return false;
                                })
                        .collect(Collectors.toList());

        // 변경 없으면 기존 참조 반환
        if (validRefs.size() == item.sourceRefs().size()) {
            return item;
        }

        return new PatchNoteItemResponse(item.text(), List.copyOf(validRefs));
    }

    private int countItems(PatchNoteDraftResponse response) {
        if (response.sections() == null) return 0;
        return response.sections().stream()
                .filter(s -> s.items() != null)
                .mapToInt(s -> s.items().size())
                .sum();
    }
}

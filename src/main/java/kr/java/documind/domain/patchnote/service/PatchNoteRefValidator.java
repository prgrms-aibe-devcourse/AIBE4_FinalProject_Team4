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

/**
 * LLM이 반환한 {@link PatchNoteDraftResponse} 내의 소스 REF를 검증·정제하는 서비스.
 *
 * <h3>검증 규칙</h3>
 * <ol>
 *   <li>화이트리스트({@link RagContext#sourceRefs()})에 없는 REF는 해당 항목의 {@code sourceRefs}에서 제거
 *   <li>{@code text}가 null이거나 공백인 항목은 전체 드롭
 *   <li>모든 항목이 드롭된 섹션은 전체 드롭
 *   <li>{@code sections}가 null이거나 비어 있으면 빈 응답 반환
 * </ol>
 *
 * <h3>의도</h3>
 * LLM 출력을 신뢰하지 않는다. 모델이 존재하지 않는 소스를 인용하거나 형식이 잘못된
 * REF를 반환하는 경우, 클라이언트에 잘못된 링크가 전달되는 것을 방지한다.
 */
@Slf4j
@Service
public class PatchNoteRefValidator {

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 파싱된 LLM 응답의 소스 REF를 검증하고 정제된 응답을 반환한다.
     *
     * <p>검증 통과율을 DEBUG/INFO 레벨로 기록한다. 환각 REF는 WARN 레벨로 개별 로깅한다.
     *
     * @param response   LLM으로부터 파싱된 원시 응답
     * @param ragContext 허용 REF 목록을 포함한 RAG 컨텍스트 (화이트리스트 소스)
     * @return 검증·정제된 응답 (섹션/항목이 드롭될 수 있음)
     */
    public PatchNoteDraftResponse validate(PatchNoteDraftResponse response, RagContext ragContext) {
        if (response.sections() == null || response.sections().isEmpty()) {
            return response;
        }

        Set<String> whitelist = Set.copyOf(ragContext.sourceRefs());

        int originalItems   = countItems(response);
        int originalSections = response.sections().size();

        List<PatchNoteSectionResponse> validSections = response.sections().stream()
                .map(section -> validateSection(section, whitelist))
                .filter(section -> section.items() != null && !section.items().isEmpty())
                .toList();

        int validItems    = validSections.stream()
                .filter(s -> s.items() != null)
                .mapToInt(s -> s.items().size())
                .sum();
        int droppedItems   = originalItems - validItems;
        int droppedSections = originalSections - validSections.size();

        if (droppedItems > 0 || droppedSections > 0) {
            log.info(
                    "패치노트 REF 검증 완료 — 항목 {}/{}개 유지, {}개 드롭, 섹션 {}/{}개 유지",
                    validItems, originalItems, droppedItems,
                    validSections.size(), originalSections);
        } else {
            log.debug(
                    "패치노트 REF 검증 완료 — 모든 항목 유효 ({}개)", validItems);
        }

        return new PatchNoteDraftResponse(response.preamble(), validSections, response.postamble());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 내부 검증 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    private PatchNoteSectionResponse validateSection(
            PatchNoteSectionResponse section, Set<String> whitelist) {

        if (section.items() == null) {
            return new PatchNoteSectionResponse(section.sectionType(), List.of());
        }

        List<PatchNoteItemResponse> validItems = section.items().stream()
                .filter(item -> item.text() != null && !item.text().isBlank())
                .map(item -> stripHallucinatedRefs(item, whitelist))
                .toList();

        return new PatchNoteSectionResponse(section.sectionType(), validItems);
    }

    /**
     * 항목의 {@code sourceRefs}에서 화이트리스트에 없는 REF를 제거한다.
     *
     * <p>모든 REF가 유효하면 기존 객체를 그대로 반환한다 (새 객체 생성 최소화).
     */
    private PatchNoteItemResponse stripHallucinatedRefs(
            PatchNoteItemResponse item, Set<String> whitelist) {

        if (item.sourceRefs() == null || item.sourceRefs().isEmpty()) {
            return item;
        }

        List<String> validRefs = item.sourceRefs().stream()
                .filter(ref -> {
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

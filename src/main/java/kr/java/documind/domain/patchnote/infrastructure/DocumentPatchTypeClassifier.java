package kr.java.documind.domain.patchnote.infrastructure;

import kr.java.documind.domain.patchnote.model.enums.PatchType;
import org.springframework.stereotype.Component;

/**
 * LLM이 분류한 카테고리 문자열을 {@link PatchType} enum으로 변환한다.
 *
 * <p>{@code extract-document-summary.st} 프롬프트의 {@code category} 필드는 "NEW" / "CHANGE" / "FIX" /
 * "MAINTENANCE" 중 하나를 반환한다. 알 수 없는 값이 들어오면 {@link PatchType#FIX}로 폴백한다.
 */
@Component
public class DocumentPatchTypeClassifier {

    /**
     * LLM 응답의 category 문자열을 PatchType으로 변환한다.
     *
     * @param categoryFromLlm LLM 분류 결과 ("NEW", "CHANGE", "FIX", "MAINTENANCE" 또는 기타)
     * @return 대응하는 PatchType (인식 불가 시 FIX 반환)
     */
    public PatchType classify(String categoryFromLlm) {
        if (categoryFromLlm == null || categoryFromLlm.isBlank()) {
            return PatchType.FIX;
        }
        return switch (categoryFromLlm.trim().toUpperCase()) {
            case "NEW" -> PatchType.NEW;
            case "CHANGE" -> PatchType.CHANGE;
            case "FIX" -> PatchType.FIX;
            case "MAINTENANCE" -> PatchType.MAINTENANCE;
            default -> PatchType.FIX;
        };
    }
}

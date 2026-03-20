package kr.java.documind.domain.patchnote.util;

import kr.java.documind.domain.issue.model.enums.IssueType;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PatchTypeResolver {

    public PatchType resolveFromIssueType(IssueType issueType) {
        if (issueType == null) {
            return PatchType.FIX;
        }
        return switch (issueType) {
            case BUG, CRASH, DATA_INCONSISTENCY, SECURITY, PAYMENT, UNKNOWN -> PatchType.FIX;
            case PERFORMANCE, NETWORK, BALANCE, UX -> PatchType.CHANGE;
            case DEPENDENCY, CONFIGURATION -> PatchType.MAINTENANCE;
        };
    }

    /**
     * LLM이 분류한 카테고리 문자열을 {@link PatchType} enum으로 변환한다.
     *
     * <p>{@code extract-document-summary.st} 프롬프트의 {@code category} 필드는 "NEW" / "CHANGE" / "FIX" /
     * "MAINTENANCE" 중 하나를 반환한다. 알 수 없는 값이 들어오면 {@link PatchType#FIX}로 폴백한다.
     *
     * @param categoryFromLlm LLM 분류 결과 ("NEW", "CHANGE", "FIX", "MAINTENANCE" 또는 기타)
     * @return 대응하는 PatchType (인식 불가 시 FIX 반환)
     */
    public PatchType resolveFromLlmCategory(String categoryFromLlm) {
        if (categoryFromLlm == null || categoryFromLlm.isBlank()) {
            return PatchType.FIX;
        }
        return switch (categoryFromLlm.trim().toUpperCase()) {
            case "NEW" -> PatchType.NEW;
            case "CHANGE" -> PatchType.CHANGE;
            case "FIX" -> PatchType.FIX;
            case "MAINTENANCE" -> PatchType.MAINTENANCE;
            default -> {
                log.warn(
                        "[PatchTypeResolver] 알 수 없는 LLM 카테고리: '{}', FIX로 폴백",
                        categoryFromLlm.trim().toUpperCase());
                yield PatchType.FIX;
            }
        };
    }
}

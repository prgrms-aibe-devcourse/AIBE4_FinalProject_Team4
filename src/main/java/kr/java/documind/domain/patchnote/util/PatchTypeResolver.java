package kr.java.documind.domain.patchnote.util;

import kr.java.documind.domain.issue.model.enums.IssueType;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import org.springframework.stereotype.Component;

@Component
public class PatchTypeResolver {

    public PatchType resolveFromIssueType(IssueType issueType) {
        if (issueType == null) {
            return PatchType.FIX;
        }
        return switch (issueType) {
            case BUG, CRASH, DATA_INCONSISTENCY, SECURITY, PAYMENT, UNKNOWN -> PatchType.FIX;
            case PERFORMANCE, NETWORK, BALANCE, UX                  -> PatchType.CHANGE;
            case DEPENDENCY, CONFIGURATION                          -> PatchType.MAINTENANCE;
        };
    }
}

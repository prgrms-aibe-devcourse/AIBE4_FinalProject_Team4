package kr.java.documind.domain.issue.model.enums;

/** 이슈 상태 */
public enum IssueStatus {
    /** 열림 (처리 필요) */
    OPEN,

    /** 수동 검토 필요 (낮은 품질의 fingerprint) */
    REQUIRES_REVIEW,

    /** 진행 중 */
    IN_PROGRESS,

    /** 해결됨 */
    RESOLVED,

    /** 무시됨 */
    IGNORED;

    public static IssueStatus fromString(String value) {
        if (value == null) {
            return OPEN;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}

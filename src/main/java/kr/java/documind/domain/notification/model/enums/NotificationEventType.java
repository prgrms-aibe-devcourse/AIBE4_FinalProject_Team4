package kr.java.documind.domain.notification.model.enums;

public enum NotificationEventType {
    ISSUE_ASSIGNED("이슈로 이동 >"),
    ISSUE_STATUS_CHANGED("이슈로 이동 >"),
    ISSUE_MENTIONED("이슈로 이동 >"),
    LOG_THRESHOLD(null),
    EMBEDDING_SUCCESS("문서 보기 >"),
    EMBEDDING_FAILED("다시 시도 >"),
    PATCHNOTE_GENERATED("패치노트 작성하기 >");

    private final String actionText;

    NotificationEventType(String actionText) {
        this.actionText = actionText;
    }

    public String getActionText() {
        return actionText;
    }
}

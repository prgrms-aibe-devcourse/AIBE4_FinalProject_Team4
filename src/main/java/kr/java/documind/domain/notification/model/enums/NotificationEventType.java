package kr.java.documind.domain.notification.model.enums;

public enum NotificationEventType {
    ISSUE_CREATED("이슈로 이동 >", NotificationMainType.ISSUE, "이슈 생성"),
    ISSUE_ASSIGNED("이슈로 이동 >", NotificationMainType.ISSUE, "담당자 할당"),
    ISSUE_STATUS_CHANGED("이슈로 이동 >", NotificationMainType.ISSUE, "상태 변경"),
    ISSUE_MENTIONED("이슈로 이동 >", NotificationMainType.ISSUE, "멘션"),
    LOG_THRESHOLD(null, NotificationMainType.LOG, "임계값 초과"),
    EMBEDDING_SUCCESS("문서 보기 >", NotificationMainType.DOCUMENT, "임베딩 완료"),
    EMBEDDING_FAILED("다시 시도 >", NotificationMainType.DOCUMENT, "임베딩 실패"),
    PATCHNOTE_DOC_GENERATED("패치노트 작성하기 >", NotificationMainType.PATCHNOTE, "문서 분석 완료"),
    PATCHNOTE_ISSUE_GENERATED("패치노트 작성하기 >", NotificationMainType.PATCHNOTE, "이슈 분석 완료");

    private final String actionText;
    private final NotificationMainType mainType;
    private final String subLabel;

    NotificationEventType(String actionText, NotificationMainType mainType, String subLabel) {
        this.actionText = actionText;
        this.mainType = mainType;
        this.subLabel = subLabel;
    }

    public String getActionText() {
        return actionText;
    }

    public NotificationMainType getMainType() {
        return mainType;
    }

    public String getSubLabel() {
        return subLabel;
    }
}

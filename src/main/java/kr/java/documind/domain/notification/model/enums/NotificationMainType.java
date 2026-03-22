package kr.java.documind.domain.notification.model.enums;

public enum NotificationMainType {
    ISSUE("이슈"),
    LOG("로그"),
    DOCUMENT("문서"),
    PATCHNOTE("패치노트");

    private final String label;

    NotificationMainType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

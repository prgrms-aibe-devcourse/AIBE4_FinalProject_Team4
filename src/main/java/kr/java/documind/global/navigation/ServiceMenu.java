package kr.java.documind.global.navigation;

import lombok.Getter;

@Getter
public enum ServiceMenu {
    DASHBOARD("dashboard", "대시보드"),
    DOCUMENTS("documents", "문서"),
    CHATBOT("chatbot", "챗봇"),
    LOGS("logs", "로그 탐색기"),
    ISSUES("issues", "이슈"),
    PATCH_NOTES("patch-notes", "패치노트"),
    ALERTS("alerts", "알림"),
    SETTINGS("settings", "설정");

    private final String key;
    private final String label;

    ServiceMenu(String key, String label) {
        this.key = key;
        this.label = label;
    }
}

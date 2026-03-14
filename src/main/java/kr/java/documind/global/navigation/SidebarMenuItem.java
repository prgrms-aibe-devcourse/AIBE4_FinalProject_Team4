package kr.java.documind.global.navigation;

public record SidebarMenuItem(
    String key,
    String label,
    String href,
    boolean disabled
) {
}

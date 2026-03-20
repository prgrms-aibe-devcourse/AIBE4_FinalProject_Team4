package kr.java.documind.global.navigation;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SidebarMenuResolver {

    public List<SidebarMenuItem> getMenus(String activeProject) {
        return List.of(
                create(ServiceMenu.DOCUMENTS, activeProject),
                create(ServiceMenu.CHATBOT, activeProject),
                create(ServiceMenu.DASHBOARD, activeProject),
                create(ServiceMenu.LOGS, activeProject),
                create(ServiceMenu.ISSUES, activeProject),
                create(ServiceMenu.PATCH_NOTES, activeProject),
                create(ServiceMenu.ALERTS, activeProject),
                create(ServiceMenu.SETTINGS, activeProject));
    }

    public SidebarMenuItem create(ServiceMenu menu, String activeProject) {
        return new SidebarMenuItem(
                menu.getKey(),
                menu.getLabel(),
                resolveHref(menu, activeProject),
                isDisabled(menu, activeProject));
    }

    private boolean isDisabled(ServiceMenu menu, String activeProject) {
        return activeProject == null && menu != ServiceMenu.DASHBOARD;
    }

    private String resolveHref(ServiceMenu menu, String activeProject) {
        return switch (menu) {
            case DASHBOARD -> activeProject != null
                    ? "/projects/" + activeProject + "/dashboard"
                    : "/member/dashboard";

            case DOCUMENTS -> activeProject != null
                    ? "/projects/" + activeProject + "/groups"
                    : "#";

            case CHATBOT -> activeProject != null ? "/projects/" + activeProject + "/chatbot" : "#";

            case LOGS -> activeProject != null ? "/projects/" + activeProject + "/logs" : "#";

            case ISSUES -> activeProject != null ? "/projects/" + activeProject + "/issues" : "#";

            case PATCH_NOTES -> activeProject != null
                    ? "/projects/" + activeProject + "/patch-note/pending-items"
                    : "#";

            case ALERTS -> activeProject != null ? "/projects/" + activeProject + "/alerts" : "#";

            case SETTINGS -> activeProject != null
                    ? "/projects/" + activeProject + "/settings"
                    : "#";
        };
    }
}

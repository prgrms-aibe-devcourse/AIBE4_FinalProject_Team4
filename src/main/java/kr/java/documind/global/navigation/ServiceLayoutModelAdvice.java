package kr.java.documind.global.navigation;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.auth.web.ProjectContextHolder;
import kr.java.documind.domain.member.model.dto.ProjectSummary;
import kr.java.documind.domain.member.service.ProjectService;
import kr.java.documind.global.annotation.ProjectPage;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class ServiceLayoutModelAdvice {

    private final SidebarMenuResolver sidebarMenuResolver;
    private final ProjectService projectService;

    @ModelAttribute
    public void addLayoutData(
            @AuthenticationPrincipal CustomUserDetails authMember,
            HttpServletRequest request,
            Model model) {

        if (request.getServletPath().startsWith("/api/")) {
            return;
        }
        if (authMember == null) {
            return;
        }

        ProjectRequestContext ctx = ProjectContextHolder.get(request).orElse(null);
        String activeProject = ctx != null ? ctx.publicId() : null;
        String currentProjectName = ctx != null ? ctx.projectName() : null;

        ServiceMenu currentMenu = resolveCurrentMenu(request);

        if (!model.containsAttribute("activeProject")) {
            model.addAttribute("activeProject", activeProject);
        }

        if (!model.containsAttribute("currentProjectName")) {
            model.addAttribute("currentProjectName", currentProjectName);
        }

        if (currentMenu != null && !model.containsAttribute("activeMenu")) {
            model.addAttribute("activeMenu", currentMenu.getKey());
        }

        if (currentMenu != null && !model.containsAttribute("pageTitle")) {
            String pageTitle =
                    currentProjectName != null
                            ? currentMenu.getLabel() + " - " + currentProjectName
                            : currentMenu.getLabel();
            model.addAttribute("pageTitle", pageTitle);
        }

        if (!model.containsAttribute("menus")) {
            model.addAttribute("menus", sidebarMenuResolver.getMenus(activeProject));
        }

        @SuppressWarnings("unchecked")
        List<ProjectSummary> projectList = (List<ProjectSummary>) model.asMap().get("projectList");
        if (projectList == null) {
            projectList = projectService.getProjectSelectorList(authMember.getMemberId());
            model.addAttribute("projectList", projectList);
        }

        if (!model.containsAttribute("currentProjectSummary")) {
            List<ProjectSummary> list = projectList;
            ProjectSummary currentProjectSummary =
                    activeProject != null
                            ? list.stream()
                                    .filter(p -> p.publicId().equals(activeProject))
                                    .findFirst()
                                    .orElse(null)
                            : null;
            model.addAttribute("currentProjectSummary", currentProjectSummary);
        }
    }

    private ServiceMenu resolveCurrentMenu(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }

        ProjectPage annotation =
                AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getMethod(), ProjectPage.class);

        if (annotation != null) {
            return annotation.value();
        }

        annotation =
                AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getBeanType(), ProjectPage.class);

        return annotation != null ? annotation.value() : null;
    }
}

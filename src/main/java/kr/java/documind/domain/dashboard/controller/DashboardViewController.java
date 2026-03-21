package kr.java.documind.domain.dashboard.controller;

import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.ProjectPage;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.navigation.ServiceMenu;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
public class DashboardViewController {

    @ProjectPage(ServiceMenu.DASHBOARD)
    @RequireProjectMember
    @GetMapping("/{publicId}/dashboard")
    public String dashboardPage(@CurrentProject ProjectRequestContext ctx, Model model) {
        model.addAttribute("publicId", ctx.publicId());
        return "dashboard/main";
    }
}

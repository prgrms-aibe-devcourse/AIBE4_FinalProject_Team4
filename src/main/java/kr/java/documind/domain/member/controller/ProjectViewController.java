package kr.java.documind.domain.member.controller;

import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.member.model.dto.ProjectSettingPageData;
import kr.java.documind.domain.member.service.ProjectService;
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
public class ProjectViewController {

    private final ProjectService projectService;

    @ProjectPage(ServiceMenu.SETTINGS)
    @RequireProjectMember
    @GetMapping("/{publicId}/settings")
    public String settings(@CurrentProject ProjectRequestContext ctx, Model model) {

        ProjectSettingPageData pageData =
                projectService.getProjectSettingPageData(ctx.publicId(), ctx.actorMemberId());
        model.addAttribute("data", pageData);
        return "member/project-setting";
    }
}

package kr.java.documind.domain.member.controller;

import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.member.service.ProjectService;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.RequireProjectMember;
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

    @RequireProjectMember
    @GetMapping("/{publicId}/settings")
    public String settings(@CurrentProject ProjectRequestContext ctx, Model model) {

        var pageData =
                projectService.getProjectSettingPageData(ctx.publicId(), ctx.actorMemberId());
        model.addAttribute("headerInfo", pageData.headerInfo());
        model.addAttribute("data", pageData);
        return "member/project-setting";
    }
}

package kr.java.documind.domain.member.controller;

import kr.java.documind.domain.member.service.MemberService;
import kr.java.documind.domain.member.service.ProjectService;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectViewController {

    private final MemberService memberService;
    private final ProjectService projectService;

    @RequireProjectMember
    @GetMapping("/{publicId}/settings")
    public String settings(
            @PathVariable String publicId,
            @AuthenticationPrincipal CustomUserDetails authMember,
            Model model) {

        var pageData = projectService.getProjectSettingPageData(publicId, authMember.getMemberId());
        model.addAttribute("headerInfo", pageData.headerInfo());
        model.addAttribute("data", pageData);
        return "member/project-setting";
        // DeletedProjectException → interceptor 에서 선처리 →
        // GlobalViewExceptionHandler.handleDeletedProject
        // AccessDeniedException  → @PreAuthorize 실패     →
        // GlobalViewExceptionHandler.handleAccessDenied
        // 그 외 예외(NotFoundException 등)               →
        // GlobalViewExceptionHandler.handleBusinessException
    }
}

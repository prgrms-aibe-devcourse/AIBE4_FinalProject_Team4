package kr.java.documind.domain.member.controller;

import jakarta.servlet.http.HttpServletRequest;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.auth.service.CurrentUserViewContextService;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.service.MemberService;
import kr.java.documind.domain.member.service.MemberService.CompanyPageData;
import kr.java.documind.domain.member.service.MemberService.ProfilePageData;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/my")
@RequiredArgsConstructor
public class MyPageViewController {

    private final MemberService memberService;
    private final CurrentUserViewContextService currentUserViewContextService;

    @GetMapping("/profile")
    public String profile(
            @AuthenticationPrincipal CustomUserDetails authMember,
            HttpServletRequest request,
            Model model) {

        Member member =
                currentUserViewContextService.getCurrentMember(authMember.getMemberId(), request);
        ProfilePageData pageData = memberService.getProfilePageData(member);

        model.addAttribute("profile", pageData.profileDetail());
        model.addAttribute("activeMenu", "profile");
        model.addAttribute("showSidebar", true);
        return "member/my-profile";
    }

    @GetMapping("/company")
    public String company(
            @AuthenticationPrincipal CustomUserDetails authMember,
            HttpServletRequest request,
            Model model) {

        if (authMember.getGlobalRole() == GlobalRole.ADMIN) {
            return "redirect:/admin/companies";
        }

        if (authMember.getGlobalRole() == GlobalRole.EMPLOYEE) {
            return "redirect:/my/profile";
        }

        Member member =
                currentUserViewContextService.getCurrentMember(authMember.getMemberId(), request);
        CompanyPageData pageData = memberService.getCompanyPageData(member);

        model.addAttribute("company", pageData.companyDetail());
        model.addAttribute("activeMenu", "company");
        model.addAttribute("showSidebar", true);
        return "member/company";
    }

    @GetMapping("/projects")
    public String projects(Model model) {
        model.addAttribute("showSidebar", true);
        model.addAttribute("activeMenu", "projects");
        return "member/dashboard";
    }
}

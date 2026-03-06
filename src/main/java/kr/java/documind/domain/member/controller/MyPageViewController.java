package kr.java.documind.domain.member.controller;

import kr.java.documind.domain.member.model.dto.HeaderInfo;
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

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal CustomUserDetails authMember, Model model) {

        ProfilePageData pageData = memberService.getProfilePageData(authMember.getMemberId());

        model.addAttribute("headerInfo", pageData.headerInfo());
        model.addAttribute("profile", pageData.profileDetail());
        return "member/my-profile";
    }

    @GetMapping("/company")
    public String company(@AuthenticationPrincipal CustomUserDetails authMember, Model model) {

        CompanyPageData pageData = memberService.getCompanyPageData(authMember.getMemberId());

        model.addAttribute("headerInfo", pageData.headerInfo());
        model.addAttribute("company", pageData.companyDetail());
        return "member/company";
    }

    @GetMapping("/projects")
    public String projects(@AuthenticationPrincipal CustomUserDetails authMember, Model model) {

        HeaderInfo headerInfo = memberService.getHeaderInfo(authMember.getMemberId());

        model.addAttribute("headerInfo", headerInfo);
        model.addAttribute("showSidebar", true);
        return "member/dashboard";
    }
}

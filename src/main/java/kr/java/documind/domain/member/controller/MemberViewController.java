package kr.java.documind.domain.member.controller;

import kr.java.documind.global.security.jwt.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/member")
public class MemberViewController {

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails authMember, Model model) {
        model.addAttribute("memberId", authMember.getMemberId());
        model.addAttribute("role", authMember.getGlobalRole());
        return "member/dashboard";
    }

    @GetMapping("/my-profile")
    public String myProfile(@AuthenticationPrincipal CustomUserDetails authMember, Model model) {
        model.addAttribute("memberId", authMember.getMemberId());
        model.addAttribute("role", authMember.getGlobalRole());
        return "member/my-profile";
    }

    @GetMapping("/company")
    public String company(@AuthenticationPrincipal CustomUserDetails authMember, Model model) {
        model.addAttribute("memberId", authMember.getMemberId());
        model.addAttribute("role", authMember.getGlobalRole());
        return "member/company";
    }
}

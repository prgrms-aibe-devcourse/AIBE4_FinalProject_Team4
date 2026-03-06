package kr.java.documind.domain.member.controller;

import kr.java.documind.domain.member.model.dto.HeaderInfo;
import kr.java.documind.domain.member.service.MemberService;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberViewController {

    private final MemberService memberService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails authMember, Model model) {

        HeaderInfo headerInfo = memberService.getHeaderInfo(authMember.getMemberId());

        model.addAttribute("headerInfo", headerInfo);
        model.addAttribute("showSidebar", false);
        return "member/dashboard";
    }
}

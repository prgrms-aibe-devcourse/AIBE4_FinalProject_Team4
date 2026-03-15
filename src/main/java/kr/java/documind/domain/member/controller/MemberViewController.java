package kr.java.documind.domain.member.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/member")
public class MemberViewController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("showSidebar", false);
        model.addAttribute("showLogoInBar", true);
        return "member/dashboard";
    }
}

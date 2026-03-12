package kr.java.documind.domain.auth.controller;

import kr.java.documind.global.security.jwt.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/auth")
public class AuthViewController {

    @GetMapping("/login")
    public String loginPage(
            @AuthenticationPrincipal CustomUserDetails authMember,
            @RequestParam(required = false) String flow,
            @RequestParam(required = false) String redirect,
            Model model) {

        if (authMember != null) {
            return "redirect:/member/dashboard";
        }

        model.addAttribute("isInviteFlow", "invite".equals(flow));
        model.addAttribute("redirectUrl", redirect);
        return "auth/login";
    }
}

package kr.java.documind.domain.member.controller;

import kr.java.documind.global.security.jwt.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/auth")
public class AuthViewController {

    @GetMapping("/login")
    public String loginPage(@AuthenticationPrincipal CustomUserDetails authMember) {
        if (authMember != null) {
            return "redirect:/member/dashboard";
        }
        return "auth/login";
    }
}

package kr.java.documind.global;

import kr.java.documind.global.security.jwt.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeViewController {

    @GetMapping("/")
    public String home(@AuthenticationPrincipal CustomUserDetails authMember) {
        if (authMember != null) {
            return "redirect:/member/dashboard";
        }
        return "index";
    }
}

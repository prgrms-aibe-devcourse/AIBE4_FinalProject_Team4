package kr.java.documind.domain.member.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/preview")
public class PreviewViewController {

    @GetMapping("/components")
    public String components(Model model) {
        // base-layout 사용 (사이드바 없는 컴포넌트 쇼케이스)
        model.addAttribute("pageTitle", "UI 컴포넌트 라이브러리");
        return "common/component-preview";
    }
}

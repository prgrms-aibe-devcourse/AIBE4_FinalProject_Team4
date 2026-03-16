package kr.java.documind.domain.auth.controller;

import java.time.LocalDateTime;
import kr.java.documind.domain.auth.model.enums.ProjectRole;
import kr.java.documind.domain.member.model.dto.InviteViewData;
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

    @GetMapping("/invite")
    public String invitePage(Model model) {
        model.addAttribute("pageTitle", "초대 메일 페이지");
        return "email/invitation";
    }

    @GetMapping("/invite-confirm")
    public String inviteConfirmPage(Model model) {
        InviteViewData data =
                new InviteViewData(
                        "",
                        "미리보기 프로젝트",
                        "1",
                        "김도큐",
                        ProjectRole.MANAGER,
                        false,
                        false,
                        null,
                        LocalDateTime.now());
        model.addAttribute("data", data);
        model.addAttribute("pageTitle", "초대 수락 페이지");
        return "member/invite-confirm";
    }

    @GetMapping("/invite-company-leave")
    public String inviteLeavePage(Model model) {
        InviteViewData data =
                new InviteViewData(
                        "",
                        "미리보기 프로젝트",
                        "1",
                        "김도큐",
                        ProjectRole.MANAGER,
                        true,
                        true,
                        "탈퇴할 회사",
                        LocalDateTime.now());
        model.addAttribute("data", data);
        model.addAttribute("pageTitle", "초대- 탈퇴 페이지");
        return "member/invite-company-leave";
    }

    @GetMapping("/invite-error")
    public String inviteErrorPage(Model model) {
        model.addAttribute("pageTitle", "초대 에러 페이지");
        model.addAttribute("errorMessage", "초대받은 이메일 - 과 현재 로그인된 계정의 이메일이 다릅니다.");
        model.addAttribute("mismatch", true);
        return "member/invite-error";
    }

    @GetMapping("/400")
    public String error400(Model model) {
        model.addAttribute("pageTitle", "400");
        return "error/400";
    }

    @GetMapping("/401")
    public String error401(Model model) {
        model.addAttribute("pageTitle", "401");
        return "error/401";
    }

    @GetMapping("/403")
    public String error403(Model model) {
        model.addAttribute("pageTitle", "403");
        return "error/403";
    }

    @GetMapping("/404")
    public String error404(Model model) {
        model.addAttribute("pageTitle", "404");
        return "error/404";
    }

    @GetMapping("/409")
    public String error409(Model model) {
        model.addAttribute("pageTitle", "409");
        return "error/409";
    }

    @GetMapping("/500")
    public String error500(Model model) {
        model.addAttribute("pageTitle", "500");
        return "error/500";
    }

    @GetMapping("/deleted-project")
    public String deletedProjectPage(Model model) {
        model.addAttribute("pageTitle", "deleted-project");
        return "error/deleted-project";
    }

    @GetMapping("/error")
    public String error(Model model) {
        model.addAttribute("pageTitle", "error");
        return "error/error";
    }
}

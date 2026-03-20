package kr.java.documind.domain.notification.controller;

import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.ProjectPage;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.navigation.ServiceMenu;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
public class NotificationViewController {

    /**
     * 알림 이력 페이지 진입 @ProjectPage: 사이드바 메뉴 활성화 상태 제어 (ServiceMenu.NOTIFICATIONS
     * 가정) @RequireProjectMember: 프로젝트 멤버 권한 체크
     */
    @ProjectPage(ServiceMenu.ALERTS)
    @RequireProjectMember
    @GetMapping("/{publicId}/alerts") // 에러 로그에 나타난 경로와 일치시킴
    public String historyPage(@CurrentProject ProjectRequestContext ctx, Model model) {
        // history.html에서 필요한 데이터를 모델에 담아 전달
        model.addAttribute("publicId", ctx.publicId());
        model.addAttribute("projectId", ctx.projectId()); // history.html의 PROJECT_ID 상수에 매핑

        return "notification/history"; // templates/notification/history.html 호출
    }
}

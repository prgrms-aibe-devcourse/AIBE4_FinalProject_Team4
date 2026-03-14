package kr.java.documind.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import kr.java.documind.domain.auth.model.dto.HeaderInfo;
import kr.java.documind.domain.auth.model.dto.UserViewContext;
import kr.java.documind.domain.auth.service.CurrentUserViewContextService;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class HeaderModelAdvice {
    private final CurrentUserViewContextService currentUserViewContextService;

    @ModelAttribute
    public void addHeaderInfo(
            @AuthenticationPrincipal CustomUserDetails authMember,
            HttpServletRequest request,
            Model model) {
        if (request.getServletPath().startsWith("/api/")) {
            return;
        }
        if (authMember == null || model.containsAttribute("userViewContext")) {
            return;
        }

        HeaderInfo headerInfo =
                currentUserViewContextService.getHeaderInfo(authMember.getMemberId(), request);

        model.addAttribute("userViewContext", new UserViewContext(headerInfo));
        model.addAttribute("headerInfo", headerInfo);
    }
}

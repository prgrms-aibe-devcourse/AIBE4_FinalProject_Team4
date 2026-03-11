package kr.java.documind.domain.member.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import kr.java.documind.domain.auth.exception.AlreadyProjectMemberException;
import kr.java.documind.domain.member.exception.InvalidInviteTokenException;
import kr.java.documind.domain.member.exception.InviteEmailMismatchException;
import kr.java.documind.domain.member.model.dto.InviteViewData;
import kr.java.documind.domain.member.service.InvitationService;
import kr.java.documind.global.config.JwtProperties;
import kr.java.documind.global.exception.BusinessException;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import kr.java.documind.global.security.oauth.HttpCookieOAuth2AuthorizationRequestRepository;
import kr.java.documind.global.util.CookieUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class InviteViewController {

    private final InvitationService invitationService;
    private final CookieUtil cookieUtil;
    private final JwtProperties jwtProperties;

    @GetMapping("/invite")
    public String showInvite(
            @RequestParam String token,
            @AuthenticationPrincipal CustomUserDetails auth,
            HttpServletResponse response,
            Model model) {

        if (auth == null) {
            String redirectUrl = "/invite?token=" + token;
            cookieUtil.addCookie(
                    response,
                    HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_AFTER_LOGIN_COOKIE,
                    redirectUrl,
                    600L,
                    jwtProperties.isCookieSecure());
            log.info("[InviteViewController] 미인증 접근 → 로그인 후 복귀 경로 저장: {}", redirectUrl);

            String encodedRedirect = URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8);
            return "redirect:/auth/login?flow=invite&redirect=" + encodedRedirect;
        }

        try {
            InviteViewData data = invitationService.getInviteViewData(token, auth.getMemberId());
            model.addAttribute("data", data);

            if (data.hasDifferentCompany()) {
                return "member/invite-company-leave";
            }

            return "member/invite-confirm";

        } catch (AlreadyProjectMemberException e) {
            return "redirect:/projects/" + e.getProjectPublicId() + "/groups";
        } catch (InviteEmailMismatchException e) {
            model.addAttribute(
                    "errorMessage",
                    "초대받은 이메일(" + e.getExpectedEmail() + ")과 현재 로그인된 계정의 이메일이 다릅니다.");
            model.addAttribute("mismatch", true);
            return "member/invite-error";
        } catch (InvalidInviteTokenException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "member/invite-error";
        } catch (BusinessException e) {
            // 삭제된 프로젝트 등 기타 BusinessException
            model.addAttribute("errorMessage", e.getMessage());
            return "member/invite-error";
        }
    }

    @PostMapping("/invite/accept")
    public String acceptInvite(
            @RequestParam String token,
            @RequestParam(defaultValue = "false") boolean forceLeaveCompany,
            @AuthenticationPrincipal CustomUserDetails auth,
            RedirectAttributes redirectAttributes) {

        log.info("[InviteViewController] acceptInvite 요청 받음: token={}", token);

        try {
            String publicId =
                    invitationService.acceptInvitation(
                            token, auth.getMemberId(), forceLeaveCompany);
            redirectAttributes.addFlashAttribute("topToast", "프로젝트에 합류했습니다!");
            return "redirect:/projects/" + publicId + "/groups";

        } catch (BusinessException e) {
            // 수락 실패 → 초대 페이지로 돌아가며 에러 메시지 표시
            redirectAttributes.addFlashAttribute("topToast", e.getMessage());
            return "redirect:/invite?token=" + token;
        }
    }
}

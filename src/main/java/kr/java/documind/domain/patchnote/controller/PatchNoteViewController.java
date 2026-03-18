package kr.java.documind.domain.patchnote.controller;

import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.ProjectPage;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.navigation.ServiceMenu;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 패치노트 도메인 화면 컨트롤러 (SSR). */
@Controller
@RequireProjectMember
@RequiredArgsConstructor
@ProjectPage(ServiceMenu.PATCH_NOTES)
public class PatchNoteViewController {

    /**
     * 저장된 패치노트 목록 페이지.
     *
     * <p>데이터는 페이지 로드 후 JavaScript가
     * {@code /api/projects/{publicId}/patch-note}를 호출하여 채운다.
     */
    @GetMapping("/projects/{publicId}/patch-note")
    public String patchNoteList(@CurrentProject ProjectRequestContext ctx, Model model) {
        model.addAttribute("publicId", ctx.publicId());
        return "patchnote/patch-note-list";
    }

    /**
     * 패치노트 피드(Pending Item) 관리 페이지 — 패치노트 작성 탭.
     *
     * <p>데이터는 페이지 로드 후 JavaScript가
     * {@code /api/projects/{publicId}/patch-note/pending-items}를 호출하여 채운다.
     */
    @GetMapping("/projects/{publicId}/patch-note/pending-items")
    public String pendingItemFeed(@CurrentProject ProjectRequestContext ctx, Model model) {
        model.addAttribute("publicId", ctx.publicId());
        return "patchnote/pending-item-feed";
    }

    /**
     * 패치노트 초안 생성 결과 리포트 페이지.
     *
     * <p>SSE 스트리밍 파라미터는 sessionStorage를 통해 전달되며,
     * 페이지 로드 후 JavaScript가 SSE 연결을 시작한다.
     */
    @GetMapping("/projects/{publicId}/patch-note/draft")
    public String patchNoteDraft(@CurrentProject ProjectRequestContext ctx, Model model) {
        model.addAttribute("publicId", ctx.publicId());
        return "patchnote/patch-note-draft";
    }
}

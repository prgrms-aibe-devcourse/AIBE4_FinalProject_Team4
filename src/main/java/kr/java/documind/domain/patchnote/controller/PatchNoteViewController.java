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

@Controller
@RequireProjectMember
@RequiredArgsConstructor
@ProjectPage(ServiceMenu.PATCH_NOTES)
public class PatchNoteViewController {

    @GetMapping("/projects/{publicId}/patch-note")
    public String patchNoteList(@CurrentProject ProjectRequestContext ctx, Model model) {
        model.addAttribute("publicId", ctx.publicId());
        return "patchnote/patch-note-list";
    }

    @GetMapping("/projects/{publicId}/patch-note/pending-items")
    public String pendingItemFeed(@CurrentProject ProjectRequestContext ctx, Model model) {
        model.addAttribute("publicId", ctx.publicId());
        return "patchnote/pending-item-feed";
    }

    @GetMapping("/projects/{publicId}/patch-note/draft")
    public String patchNoteDraft(@CurrentProject ProjectRequestContext ctx, Model model) {
        model.addAttribute("publicId", ctx.publicId());
        return "patchnote/patch-note-draft";
    }
}

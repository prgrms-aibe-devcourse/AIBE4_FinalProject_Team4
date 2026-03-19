package kr.java.documind.domain.archive.document.controller;

import kr.java.documind.domain.archive.document.model.dto.response.DocumentDetailResponse;
import kr.java.documind.domain.archive.document.service.DocumentMetadataService;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.ProjectPage;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.navigation.ServiceMenu;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequireProjectMember
@ProjectPage(ServiceMenu.DOCUMENTS)
@RequiredArgsConstructor
public class DocumentViewController {

    private final DocumentMetadataService documentMetadataService;

    @GetMapping("/projects/{publicId}/groups")
    public String documentMainPage(@CurrentProject ProjectRequestContext project, Model model) {
        model.addAttribute("publicId", project.publicId());
        return "document/main";
    }

    @GetMapping("/projects/{publicId}/documents/{documentId}")
    public String documentDetailPage(
            @CurrentProject ProjectRequestContext project,
            @PathVariable Long documentId,
            @RequestParam(required = false) Integer page,
            Model model) {
        DocumentDetailResponse detail =
                documentMetadataService.getDocumentDetail(project.projectId(), documentId);

        model.addAttribute("publicId", project.publicId());
        model.addAttribute("documentId", documentId);
        model.addAttribute("document", detail);
        model.addAttribute("page", page);

        return "document/detail";
    }
}

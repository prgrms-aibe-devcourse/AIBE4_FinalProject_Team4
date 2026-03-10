package kr.java.documind.domain.archive.document.controller;

import java.util.UUID;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentDetailResponse;
import kr.java.documind.domain.archive.document.service.DocumentMetadataService;
import kr.java.documind.global.annotation.ProjectId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class DocumentViewController {

    private final DocumentMetadataService documentService;

    @GetMapping("/projects/{publicId}/groups")
    public String documentMainPage(
            @ProjectId UUID projectId, @PathVariable String publicId, Model model) {
        model.addAttribute("publicId", publicId);
        return "document/main";
    }

    @GetMapping("/projects/{publicId}/documents/{documentId}")
    public String documentDetailPage(
            @ProjectId UUID projectId,
            @PathVariable String publicId,
            @PathVariable Long documentId,
            Model model) {
        DocumentDetailResponse detail = documentService.getDocumentDetail(projectId, documentId);

        model.addAttribute("publicId", publicId);
        model.addAttribute("documentId", documentId);
        model.addAttribute("document", detail);

        return "document/detail";
    }
}

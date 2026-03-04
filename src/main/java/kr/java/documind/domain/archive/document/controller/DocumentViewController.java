package kr.java.documind.domain.archive.document.controller;

import java.util.UUID;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentDetailResponse;
import kr.java.documind.domain.archive.document.model.dto.response.DocumentGroupResponse;
import kr.java.documind.domain.archive.document.service.DocumentGroupService;
import kr.java.documind.domain.archive.document.service.DocumentMetadataService;
import kr.java.documind.global.annotation.ProjectId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class DocumentViewController {

    private final DocumentGroupService documentGroupService;
    private final DocumentMetadataService documentMetadataService;

    @GetMapping("/projects/{publicId}/groups")
    public String documentMainPage(
            @PathVariable String publicId, @ProjectId UUID projectId, Model model) {
        Page<DocumentGroupResponse> groups =
                documentGroupService.getDocumentGroups(
                        projectId, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "groupName")));

        model.addAttribute("publicId", publicId);
        model.addAttribute("groups", groups.getContent());
        model.addAttribute("currentPage", groups.getNumber());
        model.addAttribute("totalPages", groups.getTotalPages());
        model.addAttribute("totalElements", groups.getTotalElements());

        return "document/main";
    }

    @GetMapping("/projects/{publicId}/documents/{documentId}")
    public String documentDetailPage(
            @PathVariable String publicId, @PathVariable Long documentId, Model model) {
        DocumentDetailResponse detail = documentMetadataService.getDocumentDetail(documentId);

        model.addAttribute("publicId", publicId);
        model.addAttribute("documentId", documentId);
        model.addAttribute("document", detail);

        return "document/detail";
    }
}

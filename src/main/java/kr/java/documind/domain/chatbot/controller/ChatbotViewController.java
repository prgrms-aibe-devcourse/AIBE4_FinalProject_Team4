package kr.java.documind.domain.chatbot.controller;

import java.util.List;
import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.chatbot.model.dto.response.ChatModelInfoResponse;
import kr.java.documind.domain.chatbot.service.ChatbotMetaService;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.ProjectPage;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.navigation.ServiceMenu;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/projects/{publicId}/chatbot")
@RequireProjectMember
@ProjectPage(ServiceMenu.CHATBOT)
@RequiredArgsConstructor
public class ChatbotViewController {

    private final ChatbotMetaService chatbotMetaService;
    private final DocumentMetadataManager documentMetadataManager;

    @GetMapping
    public String chatbotMainPage(
            @CurrentProject ProjectRequestContext project,
            @RequestParam(required = false) Long documentId,
            Model model) {
        List<ChatModelInfoResponse> chatModels = chatbotMetaService.getChatModels();

        model.addAttribute("publicId", project.publicId());
        model.addAttribute("chatModels", chatModels);

        if (documentId != null) {
            DocumentMetadata doc =
                    documentMetadataManager.getByIdAndProjectId(documentId, project.projectId());
            model.addAttribute("fixedDocumentId", documentId);
            model.addAttribute(
                    "fixedDocumentName", doc.getDocumentName() + "." + doc.getExtension());
            model.addAttribute("fixedDocumentExtension", doc.getExtension());
        }

        return "chatbot/main";
    }
}

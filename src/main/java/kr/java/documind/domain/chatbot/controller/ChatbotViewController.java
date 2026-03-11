package kr.java.documind.domain.chatbot.controller;

import java.util.List;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.chatbot.model.dto.response.ChatModelInfoResponse;
import kr.java.documind.domain.chatbot.service.ChatbotMetaService;
import kr.java.documind.global.annotation.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/projects/{publicId}/chatbot")
@RequiredArgsConstructor
public class ChatbotViewController {

    private final ChatbotMetaService chatbotMetaService;

    @GetMapping
    public String chatbotMainPage(
        @CurrentProject ProjectRequestContext project, Model model) {
        List<ChatModelInfoResponse> chatModels = chatbotMetaService.getChatModels();

        model.addAttribute("publicId", project.publicId());
        model.addAttribute("chatModels", chatModels);

        return "chatbot/main";
    }
}

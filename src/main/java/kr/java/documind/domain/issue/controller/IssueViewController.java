package kr.java.documind.domain.issue.controller;

import java.util.UUID;
import kr.java.documind.global.annotation.ProjectId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class IssueViewController {

    @GetMapping("/projects/{publicId}/issues")
    public String issueListPage(
            @ProjectId UUID projectId, @PathVariable String publicId, Model model) {
        model.addAttribute("publicId", publicId);
        model.addAttribute("projectId", projectId);
        return "issue/issue-list";
    }
}

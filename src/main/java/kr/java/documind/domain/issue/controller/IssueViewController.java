package kr.java.documind.domain.issue.controller;

import java.util.UUID;
import kr.java.documind.global.annotation.ProjectId;
import kr.java.documind.global.annotation.ProjectPage;
import kr.java.documind.global.navigation.ServiceMenu;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
@ProjectPage(ServiceMenu.ISSUES)
public class IssueViewController {

    @GetMapping("/projects/{publicId}/issues")
    public String issueListPage(
            @ProjectId UUID projectId, @PathVariable String publicId, Model model) {
        model.addAttribute("publicId", publicId);
        model.addAttribute("projectId", projectId);
        return "issue/issue-list";
    }

    @GetMapping("/projects/{publicId}/issues/{issueId}/analysis")
    public String issueAnalysisPage(
            @ProjectId UUID projectId,
            @PathVariable String publicId,
            @PathVariable Long issueId,
            Model model) {
        model.addAttribute("publicId", publicId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("issueId", issueId);
        return "issue/issue-analysis";
    }
}

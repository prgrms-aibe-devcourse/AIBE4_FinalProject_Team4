package kr.java.documind.domain.member.controller;

import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.member.service.CompanyService;
import kr.java.documind.domain.member.service.CompanyService.AdminPageData;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminViewController {

    private final CompanyService companyService;

    @GetMapping("/companies")
    public String companyAdmin(@AuthenticationPrincipal CustomUserDetails authMember, Model model) {

        if (authMember.getGlobalRole() != GlobalRole.ADMIN) {
            return "redirect:/my/company";
        }

        AdminPageData pageData = companyService.getAdminCompanyPageData();

        model.addAttribute("activeMenu", "company");
        model.addAttribute("showSidebar", true);
        model.addAttribute("pending", pageData.pendingCompanies());
        model.addAttribute("approved", pageData.approvedCompanies());
        model.addAttribute("suspended", pageData.suspendedCompanies());
        model.addAttribute("pendingCount", pageData.pendingCount());
        model.addAttribute("approvedCount", pageData.approvedCount());
        model.addAttribute("suspendedCount", pageData.suspendedCount());
        return "member/company-admin";
    }
}

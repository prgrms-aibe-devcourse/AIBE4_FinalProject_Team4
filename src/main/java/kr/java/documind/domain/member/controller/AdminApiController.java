package kr.java.documind.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.java.documind.domain.member.service.CompanyService;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin API", description = "관리자 전용 API")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final CompanyService companyService;

    @Operation(summary = "회사 승인", description = "운영자가 회사 가입 신청을 승인합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/companies/{companyId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveCompany(
            @AuthenticationPrincipal CustomUserDetails authMember, @PathVariable Long companyId) {

        companyService.approveCompany(authMember.getMemberId(), companyId);
        return ResponseEntity.ok(ApiResponse.success("회사가 승인되었습니다."));
    }

    @Operation(summary = "회사 거부", description = "운영자가 회사 가입 신청을 거부합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/companies/{companyId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectCompany(
            @AuthenticationPrincipal CustomUserDetails authMember, @PathVariable Long companyId) {

        companyService.rejectCompany(authMember.getMemberId(), companyId);
        return ResponseEntity.ok(ApiResponse.success("회사가 거부되었습니다."));
    }
}

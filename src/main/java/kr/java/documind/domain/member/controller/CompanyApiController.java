package kr.java.documind.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.java.documind.domain.member.model.dto.CompanyNameRequest;
import kr.java.documind.domain.member.model.dto.ProfileImageResponse;
import kr.java.documind.domain.member.service.CompanyService;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Company", description = "회사 API")
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyApiController {

    private final CompanyService companyService;

    @Operation(summary = "회사 등록", description = "새 회사를 등록하고 승인을 요청합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> registerCompany(
            @AuthenticationPrincipal CustomUserDetails authMember,
            @Valid @RequestBody CompanyNameRequest request) {

        companyService.registerCompany(authMember.getMemberId(), request.name());
        return ResponseEntity.ok(ApiResponse.success("회사 등록이 완료되었습니다. 관리자 승인 후 이용 가능합니다."));
    }

    @Operation(summary = "회사 정보 수정", description = "소속 회사의 이름을 수정합니다.")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateCompany(
            @AuthenticationPrincipal CustomUserDetails authMember,
            @Valid @RequestBody CompanyNameRequest request) {

        companyService.updateCompanyName(authMember.getMemberId(), request.name());
        return ResponseEntity.ok(ApiResponse.success("회사 정보가 저장되었습니다."));
    }

    @Operation(
            summary = "회사 프로필 이미지 업로드",
            description = "회사 프로필 이미지를 S3에 저장하고 Pre-signed URL을 반환합니다.")
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileImageResponse>> uploadCompanyProfileImage(
            @AuthenticationPrincipal CustomUserDetails authMember,
            @RequestPart("file") MultipartFile file) {

        String profileImageUrl =
                companyService.uploadCompanyProfileImage(authMember.getMemberId(), file);
        return ResponseEntity.ok(ApiResponse.success(new ProfileImageResponse(profileImageUrl)));
    }
}

package kr.java.documind.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.java.documind.domain.member.model.dto.MemberProfileUpdateRequest;
import kr.java.documind.domain.member.model.dto.ProfileImageResponse;
import kr.java.documind.domain.member.service.MemberService;
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

@Tag(name = "Member", description = "회원 API")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;

    @Operation(summary = "프로필 수정", description = "닉네임을 수정합니다.")
    @PatchMapping("/me/profile")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails authMember,
            @Valid @RequestBody MemberProfileUpdateRequest request) {

        memberService.updateMemberProfile(authMember.getMemberId(), request.nickname());
        return ResponseEntity.ok(ApiResponse.success("변경 사항이 저장되었습니다."));
    }

    @Operation(summary = "프로필 이미지 업로드", description = "프로필 이미지를 S3에 저장하고 Pre-signed URL을 반환합니다.")
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileImageResponse>> uploadProfileImage(
            @AuthenticationPrincipal CustomUserDetails authMember,
            @RequestPart("file") MultipartFile file) {

        String profileImageUrl =
                memberService.uploadMemberProfileImage(authMember.getMemberId(), file);
        return ResponseEntity.ok(ApiResponse.success(new ProfileImageResponse(profileImageUrl)));
    }
}

package kr.java.documind.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.java.documind.domain.member.model.dto.ProfileImageResponse;
import kr.java.documind.domain.member.model.dto.ProjectCreateRequest;
import kr.java.documind.domain.member.model.dto.ProjectCreateResponse;
import kr.java.documind.domain.member.model.dto.ProjectUpdateRequest;
import kr.java.documind.domain.member.service.ProjectService;
import kr.java.documind.global.annotation.RequireProjectManager;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Project", description = "프로젝트 API")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectApiController {

    private final ProjectService projectService;

    @Operation(summary = "프로젝트 생성", description = "새 프로젝트를 생성하고 요청자를 MANAGER로 등록합니다.")
    @PreAuthorize("hasRole('CEO')")
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectCreateResponse>> createProject(
            @AuthenticationPrincipal CustomUserDetails authMember,
            @Valid @RequestBody ProjectCreateRequest request) {

        ProjectCreateResponse response =
                projectService.createProject(authMember.getMemberId(), request.name());
        return ResponseEntity.ok(ApiResponse.success("프로젝트가 생성되었습니다.", response));
    }

    @Operation(summary = "프로젝트 이름 수정", description = "프로젝트 이름을 수정합니다. MANAGER 권한이 필요합니다.")
    @RequireProjectManager
    @PatchMapping("/{publicId}")
    public ResponseEntity<ApiResponse<Void>> updateProject(
            @AuthenticationPrincipal CustomUserDetails authMember,
            @PathVariable String publicId,
            @Valid @RequestBody ProjectUpdateRequest request) {

        projectService.updateProjectName(publicId, authMember.getMemberId(), request.name());
        return ResponseEntity.ok(ApiResponse.success("프로젝트 정보가 수정되었습니다."));
    }

    @Operation(summary = "프로젝트 프로필 이미지 업로드", description = "프로젝트 프로필 이미지를 업로드합니다.")
    @RequireProjectManager
    @PostMapping(
            value = "/{publicId}/profile-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileImageResponse>> uploadProfileImage(
            @AuthenticationPrincipal CustomUserDetails authMember,
            @PathVariable String publicId,
            @RequestPart("file") MultipartFile file) {

        ProfileImageResponse response =
                projectService.uploadProjectProfileImage(authMember.getMemberId(), publicId, file);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "프로젝트 나가기", description = "현재 사용자를 프로젝트 멤버에서 제거합니다. CEO는 나갈 수 없습니다.")
    @RequireProjectMember
    @DeleteMapping("/{publicId}/members/me")
    public ResponseEntity<ApiResponse<Void>> leaveProject(
            @AuthenticationPrincipal CustomUserDetails authMember, @PathVariable String publicId) {

        projectService.leaveProject(publicId, authMember.getMemberId());
        return ResponseEntity.ok(ApiResponse.success("프로젝트에서 나갔습니다."));
    }

    @Operation(
            summary = "프로젝트 삭제",
            description = "프로젝트와 모든 멤버를 소프트 딜리트합니다. CEO이면서 MANAGER인 경우에만 삭제할 수 있습니다.")
    @PreAuthorize("hasRole('CEO') and @projectAuthz.isProjectManager()")
    @DeleteMapping("/{publicId}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @AuthenticationPrincipal CustomUserDetails authMember, @PathVariable String publicId) {

        projectService.deleteProject(publicId, authMember.getMemberId());
        return ResponseEntity.ok(ApiResponse.success("프로젝트가 삭제되었습니다."));
    }
}

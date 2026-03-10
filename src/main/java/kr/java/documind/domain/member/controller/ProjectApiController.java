package kr.java.documind.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.java.documind.domain.member.model.dto.ApiKeyIssueResponse;
import kr.java.documind.domain.member.model.dto.ApiKeyStatusUpdateRequest;
import kr.java.documind.domain.member.model.dto.ProfileImageResponse;
import kr.java.documind.domain.member.model.dto.ProjectCreateRequest;
import kr.java.documind.domain.member.model.dto.ProjectCreateResponse;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.member.model.dto.ProjectUpdateRequest;
import kr.java.documind.domain.member.service.ProjectService;
import kr.java.documind.global.annotation.CurrentProject;
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
            @CurrentProject ProjectRequestContext ctx,
            @Valid @RequestBody ProjectUpdateRequest request) {

        projectService.updateProjectName(ctx.publicId(), ctx.actorMemberId(), request.name());
        return ResponseEntity.ok(ApiResponse.success("프로젝트 정보가 수정되었습니다."));
    }

    @Operation(summary = "프로젝트 프로필 이미지 업로드", description = "프로젝트 프로필 이미지를 업로드합니다.")
    @RequireProjectManager
    @PostMapping(
            value = "/{publicId}/profile-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileImageResponse>> uploadProfileImage(
            @CurrentProject ProjectRequestContext ctx,
            @RequestPart("file") MultipartFile file) {

        ProfileImageResponse response =
                projectService.uploadProjectProfileImage(ctx.publicId(), ctx.actorMemberId(), file);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "프로젝트 나가기", description = "현재 사용자를 프로젝트 멤버에서 제거합니다. CEO는 나갈 수 없습니다.")
    @RequireProjectMember
    @DeleteMapping("/{publicId}/members/me")
    public ResponseEntity<ApiResponse<Void>> leaveProject(
            @CurrentProject ProjectRequestContext ctx) {
        projectService.leaveProject(ctx.publicId(), ctx.actorMemberId());
        return ResponseEntity.ok(ApiResponse.success("프로젝트에서 나갔습니다."));
    }

    @Operation(
            summary = "프로젝트 삭제",
            description = "프로젝트와 모든 멤버를 소프트 딜리트합니다. CEO이면서 MANAGER인 경우에만 삭제할 수 있습니다.")
    @PreAuthorize("hasRole('CEO') and @projectAuthz.isProjectManager()")
    @DeleteMapping("/{publicId}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @CurrentProject ProjectRequestContext ctx) {

        projectService.deleteProject(ctx.publicId(), ctx.actorMemberId());
        return ResponseEntity.ok(ApiResponse.success("프로젝트가 삭제되었습니다."));
    }

    @Operation(
            summary = "API 키 발급/재발급",
            description =
                    "프로젝트 API 키를 신규 발급하거나 재발급합니다. "
                            + "기존 ACTIVE/SUSPENDED 키는 즉시 REVOKE 처리됩니다. "
                            + "평문 키는 응답에서 1회만 반환됩니다. MANAGER 권한이 필요합니다.")
    @RequireProjectManager
    @PostMapping("/{publicId}/api-keys")
    public ResponseEntity<ApiResponse<ApiKeyIssueResponse>> issueApiKey(
            @CurrentProject ProjectRequestContext ctx) {

        ApiKeyIssueResponse response =
                projectService.issueApiKey(ctx.publicId(), ctx.actorMemberId());
        return ResponseEntity.ok(ApiResponse.success("API 키가 발급되었습니다.", response));
    }

    @Operation(
            summary = "API 키 상태 변경",
            description = "API 키를 ACTIVE ↔ SUSPENDED 간 전환합니다. MANAGER 권한이 필요합니다.")
    @RequireProjectManager
    @PatchMapping("/{publicId}/api-keys/status")
    public ResponseEntity<ApiResponse<Void>> toggleApiKeyStatus(
            @CurrentProject ProjectRequestContext ctx,
            @Valid @RequestBody ApiKeyStatusUpdateRequest request) {

        projectService.toggleApiKeyStatus(
                ctx.publicId(), ctx.actorMemberId(), request.status());
        return ResponseEntity.ok(ApiResponse.success("API 키 상태가 변경되었습니다."));
    }
}

package kr.java.documind.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.member.model.dto.ApiKeyIssueResponse;
import kr.java.documind.domain.member.model.dto.ApiKeyReissueRequest;
import kr.java.documind.domain.member.model.dto.ApiKeyStatusUpdateRequest;
import kr.java.documind.domain.member.model.dto.ProfileImageResponse;
import kr.java.documind.domain.member.model.dto.ProjectCreateRequest;
import kr.java.documind.domain.member.model.dto.ProjectCreateResponse;
import kr.java.documind.domain.member.model.dto.ProjectRoleUpdateRequest;
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
            @CurrentProject ProjectRequestContext ctx, @RequestPart("file") MultipartFile file) {

        ProfileImageResponse response =
                projectService.uploadProjectProfileImage(ctx.publicId(), ctx.actorMemberId(), file);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "프로젝트 멤버 권한 변경", description = "프로젝트 멤버의 권한을 변경합니다. MANAGER 권한이 필요합니다.")
    @RequireProjectManager
    @PatchMapping("/{publicId}/members/{memberId}/role")
    public ResponseEntity<ApiResponse<Void>> changeProjectMemberRole(
            @CurrentProject ProjectRequestContext ctx,
            @PathVariable UUID memberId,
            @Valid @RequestBody ProjectRoleUpdateRequest request) {

        projectService.changeProjectRole(
                ctx.publicId(), ctx.actorMemberId(), memberId, request.role());
        return ResponseEntity.ok(ApiResponse.success("멤버 권한이 변경되었습니다."));
    }

    @Operation(summary = "프로젝트 멤버 제거", description = "프로젝트에서 특정 멤버를 제거합니다. MANAGER 권한이 필요합니다.")
    @RequireProjectManager
    @DeleteMapping("/{publicId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeProjectMember(
            @CurrentProject ProjectRequestContext ctx, @PathVariable UUID memberId) {

        projectService.removeProjectMember(ctx.publicId(), ctx.actorMemberId(), memberId);
        return ResponseEntity.ok(ApiResponse.success("멤버를 제거했습니다."));
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
            summary = "전체 API 키 재발급",
            description =
                    "프로젝트의 모든 API 키(INGEST, QUERY)를 새로 발급합니다."
                            + "기존에 사용 중인 모든 키는 즉시 폐기되어 서비스가 중단될 수 있으므로 주의가 필요합니다.")
    @RequireProjectManager
    @PostMapping("/{publicId}/api-keys")
    public ResponseEntity<ApiResponse<ApiKeyIssueResponse>> issueApiKeys(
            @CurrentProject ProjectRequestContext ctx) {

        ApiKeyIssueResponse response =
                projectService.issueApiKey(ctx.publicId(), ctx.actorMemberId());
        return ResponseEntity.ok(ApiResponse.success("API 키가 발급되었습니다.", response));
    }

    @Operation(
            summary = "API 키 재발급",
            description =
                    "특정 타입(INGEST 또는 QUERY)의 API 키를 재발급합니다. "
                            + "해당 타입의 기존 ACTIVE/SUSPENDED 키는 즉시 폐기(REVOKE) 처리됩니다. "
                            + "평문 키는 이 응답에서만 반환됩니다. MANAGER 권한이 필요합니다.")
    @RequireProjectManager
    @PostMapping("/{publicId}/api-keys/reissue")
    public ResponseEntity<ApiResponse<ApiKeyIssueResponse>> reissueApiKey(
            @CurrentProject ProjectRequestContext ctx,
            @Valid @RequestBody ApiKeyReissueRequest request) {

        ApiKeyIssueResponse response =
                projectService.reissueApiKey(
                        ctx.publicId(), ctx.actorMemberId(), request.keyType());
        return ResponseEntity.ok(
                ApiResponse.success(request.keyType() + " API 키가 재발급되었습니다.", response));
    }

    @Operation(
            summary = "API 키 상태 변경",
            description =
                    "특정 타입(INGEST 또는 QUERY)의 API 키를 ACTIVE ↔ SUSPENDED 간 전환합니다. MANAGER 권한이 필요합니다.")
    @RequireProjectManager
    @PatchMapping("/{publicId}/api-keys/status")
    public ResponseEntity<ApiResponse<Void>> toggleApiKeyStatus(
            @CurrentProject ProjectRequestContext ctx,
            @Valid @RequestBody ApiKeyStatusUpdateRequest request) {

        projectService.toggleApiKeyStatus(
                ctx.publicId(), ctx.actorMemberId(), request.keyType(), request.status());
        return ResponseEntity.ok(ApiResponse.success(request.keyType() + " API 키 상태가 변경되었습니다."));
    }
}

package kr.java.documind.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.member.model.dto.ProjectMemberSimpleResponse;
import kr.java.documind.domain.member.service.ProjectService;
import kr.java.documind.global.annotation.ProjectId;
import kr.java.documind.global.annotation.RequireProjectMember;
import kr.java.documind.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project Member", description = "프로젝트 멤버 API")
@RestController
@RequestMapping("/api/projects/{publicId}/members")
@RequiredArgsConstructor
public class ProjectMemberApiController {

    private final ProjectService projectService;

    @Operation(summary = "프로젝트 멤버 목록 조회", description = "담당자 선택을 위한 프로젝트 활성 멤버 목록을 조회합니다.")
    @RequireProjectMember
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectMemberSimpleResponse>>> getProjectMembers(
            @ProjectId UUID projectId) {

        List<ProjectMemberSimpleResponse> members = projectService.getProjectMembers(projectId);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @Operation(summary = "프로젝트 멤버 닉네임 검색", description = "멘션 자동완성을 위한 프로젝트 멤버 닉네임 검색 (부분 일치)")
    @RequireProjectMember
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProjectMemberSimpleResponse>>> searchProjectMembers(
            @ProjectId UUID projectId,
            @Parameter(description = "검색 쿼리 (닉네임 부분 일치)", example = "김", required = true)
                    @RequestParam
                    String query,
            @Parameter(description = "최대 결과 수", example = "10") @RequestParam(defaultValue = "10")
                    int limit) {

        List<ProjectMemberSimpleResponse> members =
                projectService.searchProjectMembersByNickname(projectId, query, limit);
        return ResponseEntity.ok(ApiResponse.success(members));
    }
}

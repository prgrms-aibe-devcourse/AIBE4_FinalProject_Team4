package kr.java.documind.domain.issue.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kr.java.documind.domain.issue.model.dto.request.ApproveRecommendationRequest;
import kr.java.documind.domain.issue.model.dto.response.AssigneeInfo;
import kr.java.documind.domain.issue.model.dto.response.IssueDetailResponse;
import kr.java.documind.domain.issue.model.dto.response.IssueListResponse;
import kr.java.documind.domain.issue.model.dto.response.SimilarityResult;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.service.recommendation.IssueRecommendationService;
import kr.java.documind.domain.member.model.repository.MemberRepository;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Issue Recommendation", description = "이슈 추천 API (승인/거부)")
@RestController
@RequestMapping("/api/projects/{projectId}/issue-recommendations")
@RequiredArgsConstructor
public class IssueRecommendationApiController {

    private final IssueRecommendationService recommendationService;
    private final MemberRepository memberRepository;

    /**
     * 추천 이슈 목록 조회
     *
     * @param projectId 프로젝트 ID
     * @return 추천 이슈 목록
     */
    @Operation(summary = "추천 이슈 목록 조회", description = "로그 분석 결과 추천된 이슈 목록을 조회합니다 (RECOMMENDED 상태).")
    @IssueRecommendationSwaggerDocs.GetRecommendationListDocs
    @GetMapping
    public ResponseEntity<ApiResponse<List<IssueListResponse>>> getRecommendationList(
            @Parameter(
                            description = "프로젝트 ID",
                            example = "123e4567-e89b-12d3-a456-426614174000",
                            required = true)
                    @PathVariable
                    java.util.UUID projectId) {

        List<Issue> recommendations = recommendationService.getRecommendationList(projectId);

        List<IssueListResponse> response =
                recommendations.stream().map(IssueListResponse::from).toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 추천 이슈 상세 조회
     *
     * @param projectId 프로젝트 ID
     * @param issueId 이슈 ID
     * @return 추천 이슈 상세
     */
    @Operation(summary = "추천 이슈 상세 조회", description = "특정 추천 이슈의 상세 정보를 조회합니다.")
    @IssueRecommendationSwaggerDocs.GetRecommendationDetailDocs
    @GetMapping("/{issueId}")
    public ResponseEntity<ApiResponse<IssueDetailResponse>> getRecommendationDetail(
            @Parameter(
                            description = "프로젝트 ID",
                            example = "123e4567-e89b-12d3-a456-426614174000",
                            required = true)
                    @PathVariable
                    java.util.UUID projectId,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId) {

        Issue recommendation = recommendationService.getRecommendationDetail(issueId, projectId);

        // 담당자 정보 조회
        AssigneeInfo assignee = null;
        if (recommendation.getAssigneeId() != null) {
            assignee =
                    memberRepository
                            .findById(recommendation.getAssigneeId())
                            .map(AssigneeInfo::from)
                            .orElse(null);
        }

        // 유사도 분석 수행 (최대 4개)
        List<SimilarityResult> similarityResults =
                recommendationService.analyzeSimilarity(recommendation);

        IssueDetailResponse response =
                IssueDetailResponse.from(recommendation, assignee, similarityResults);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 추천 이슈 승인 → 실제 이슈 생성
     *
     * @param projectId 프로젝트 ID
     * @param issueId 이슈 ID
     * @param request 승인 요청 (담당자 ID)
     * @param authMember 현재 로그인한 사용자
     * @return 성공 메시지
     */
    @Operation(summary = "추천 이슈 승인", description = "추천 이슈를 승인하여 실제 이슈로 생성합니다 (RECOMMENDED → TODO).")
    @IssueRecommendationSwaggerDocs.ApproveRecommendationDocs
    @PostMapping("/{issueId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveRecommendation(
            @Parameter(
                            description = "프로젝트 ID",
                            example = "123e4567-e89b-12d3-a456-426614174000",
                            required = true)
                    @PathVariable
                    java.util.UUID projectId,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId,
            @RequestBody ApproveRecommendationRequest request,
            @AuthenticationPrincipal CustomUserDetails authMember) {

        recommendationService.approveRecommendation(
                issueId,
                projectId,
                request.assigneeId(),
                request.title(),
                request.description(),
                request.priority(),
                authMember.getMemberId());

        return ResponseEntity.ok(ApiResponse.success("추천 이슈가 승인되어 이슈로 생성되었습니다."));
    }

    /**
     * 추천 이슈 거부
     *
     * @param projectId 프로젝트 ID
     * @param issueId 이슈 ID
     * @param authMember 현재 로그인한 사용자
     * @return 성공 메시지
     */
    @Operation(summary = "추천 이슈 거부", description = "추천 이슈를 거부합니다 (RECOMMENDED → REJECTED).")
    @IssueRecommendationSwaggerDocs.RejectRecommendationDocs
    @PostMapping("/{issueId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectRecommendation(
            @Parameter(
                            description = "프로젝트 ID",
                            example = "123e4567-e89b-12d3-a456-426614174000",
                            required = true)
                    @PathVariable
                    java.util.UUID projectId,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId,
            @AuthenticationPrincipal CustomUserDetails authMember) {

        recommendationService.rejectRecommendation(issueId, projectId, authMember.getMemberId());

        return ResponseEntity.ok(ApiResponse.success("추천 이슈가 거부되었습니다."));
    }
}

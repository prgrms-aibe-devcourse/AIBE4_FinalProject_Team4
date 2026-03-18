package kr.java.documind.domain.issue.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import kr.java.documind.domain.issue.model.dto.request.IssueAssignRequest;
import kr.java.documind.domain.issue.model.dto.request.IssuePriorityUpdateRequest;
import kr.java.documind.domain.issue.model.dto.request.IssueStatusUpdateRequest;
import kr.java.documind.domain.issue.model.dto.response.AffectedPlayerResponse;
import kr.java.documind.domain.issue.model.dto.response.AssigneeInfo;
import kr.java.documind.domain.issue.model.dto.response.DistributionDataResponse;
import kr.java.documind.domain.issue.model.dto.response.IssueDetailResponse;
import kr.java.documind.domain.issue.model.dto.response.IssueHistoryResponse;
import kr.java.documind.domain.issue.model.dto.response.IssueListResponse;
import kr.java.documind.domain.issue.model.dto.response.OccurrenceTrendResponse;
import kr.java.documind.domain.issue.model.dto.response.RootCauseAnalysisResponse;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.entity.IssueHistory;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.service.AffectedPlayersService;
import kr.java.documind.domain.issue.service.DistributionAnalysisService;
import kr.java.documind.domain.issue.service.IssueContextService;
import kr.java.documind.domain.issue.service.OccurrenceTrendService;
import kr.java.documind.domain.issue.service.RootCauseAnalysisService;
import kr.java.documind.domain.issue.service.workflow.IssueHistoryService;
import kr.java.documind.domain.issue.service.workflow.IssueManagementService;
import kr.java.documind.domain.member.model.repository.MemberRepository;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Issue Management", description = "이슈 관리 API (담당자 할당, 상태 변경, 이력 조회)")
@RestController
@RequestMapping("/api/projects/{publicId}/issues")
@RequiredArgsConstructor
public class IssueManagementApiController {

    private final IssueManagementService issueManagementService;
    private final IssueHistoryService issueHistoryService;
    private final DistributionAnalysisService distributionAnalysisService;
    private final AffectedPlayersService affectedPlayersService;
    private final OccurrenceTrendService occurrenceTrendService;
    private final RootCauseAnalysisService rootCauseAnalysisService;
    private final IssueContextService issueContextService;
    private final MemberRepository memberRepository;

    /**
     * 프로젝트별 이슈 목록 조회
     *
     * @param ctx 프로젝트 컨텍스트
     * @param status 필터링할 상태 (선택)
     * @return 이슈 목록
     */
    @Operation(summary = "이슈 목록 조회", description = "프로젝트의 이슈 목록을 조회합니다. 상태별 필터링이 가능합니다.")
    @IssueManagementSwaggerDocs.GetIssueListDocs
    @GetMapping
    public ResponseEntity<ApiResponse<List<IssueListResponse>>> getIssueList(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "이슈 상태 (선택)", example = "TODO") @RequestParam(required = false)
                    IssueStatus status) {

        List<Issue> issues = issueManagementService.getIssueList(ctx.projectId(), status);

        List<IssueListResponse> response = issues.stream()
                .map(issue -> {
                    AssigneeInfo assignee = null;
                    if (issue.getAssigneeId() != null) {
                        assignee = memberRepository.findById(issue.getAssigneeId())
                                .map(AssigneeInfo::from)
                                .orElse(null);
                    }
                    return IssueListResponse.from(issue, assignee);
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 이슈 상세 조회
     *
     * @param ctx 프로젝트 컨텍스트
     * @param issueId 이슈 ID
     * @return 이슈 상세 정보
     */
    @Operation(summary = "이슈 상세 조회", description = "특정 이슈의 상세 정보를 조회합니다.")
    @IssueManagementSwaggerDocs.GetIssueDetailDocs
    @GetMapping("/{issueId}")
    public ResponseEntity<ApiResponse<IssueDetailResponse>> getIssueDetail(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId) {

        Issue issue = issueManagementService.getIssueDetail(issueId, ctx.projectId());

        // 담당자 정보 조회
        AssigneeInfo assignee = null;
        if (issue.getAssigneeId() != null) {
            assignee =
                    memberRepository
                            .findById(issue.getAssigneeId())
                            .map(AssigneeInfo::from)
                            .orElse(null);
        }

        IssueDetailResponse response = IssueDetailResponse.from(issue, assignee, null);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 이슈 담당자 지정
     *
     * @param ctx 프로젝트 컨텍스트
     * @param issueId 이슈 ID
     * @param request 담당자 ID
     * @param authMember 현재 로그인한 사용자 (변경자)
     * @return 성공 메시지
     */
    @Operation(summary = "이슈 담당자 지정", description = "이슈에 담당자를 할당하고 변경 이력을 기록합니다.")
    @IssueManagementSwaggerDocs.AssignIssueDocs
    @PutMapping("/{issueId}/assignee")
    public ResponseEntity<ApiResponse<Void>> assignIssue(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId,
            @Valid @RequestBody IssueAssignRequest request,
            @AuthenticationPrincipal CustomUserDetails authMember) {

        issueManagementService.assignIssue(
                issueId, ctx.projectId(), request.assigneeId(), authMember.getMemberId());

        return ResponseEntity.ok(ApiResponse.success("담당자가 지정되었습니다."));
    }

    /**
     * 이슈 상태 변경
     *
     * @param ctx 프로젝트 컨텍스트
     * @param issueId 이슈 ID
     * @param request 변경할 상태 및 패치노트 포함 여부
     * @param authMember 현재 로그인한 사용자 (변경자)
     * @return 성공 메시지
     */
    @Operation(
            summary = "이슈 상태 변경",
            description =
                    "이슈 상태를 변경하고 변경 이력을 기록합니다. " + "RESOLVED 상태로 변경 시 패치노트 반영 여부를 선택할 수 있습니다.")
    @IssueManagementSwaggerDocs.UpdateStatusDocs
    @PutMapping("/{issueId}/status")
    public ResponseEntity<ApiResponse<Void>> updateIssueStatus(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId,
            @Valid @RequestBody IssueStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails authMember) {

        issueManagementService.updateIssueStatus(
                issueId,
                ctx.projectId(),
                request.status(),
                request.resolutionNote(),
                authMember.getMemberId(),
                request.shouldIncludeInPatchNote());

        return ResponseEntity.ok(ApiResponse.success("이슈 상태가 변경되었습니다."));
    }

    /**
     * 이슈 우선순위 변경
     *
     * @param ctx 프로젝트 컨텍스트
     * @param issueId 이슈 ID
     * @param request 변경할 우선순위
     * @param authMember 현재 로그인한 사용자 (변경자)
     * @return 성공 메시지
     */
    @Operation(summary = "이슈 우선순위 변경", description = "이슈의 우선순위를 변경하고 변경 이력을 기록합니다.")
    @PutMapping("/{issueId}/priority")
    public ResponseEntity<ApiResponse<Void>> updateIssuePriority(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId,
            @Valid @RequestBody IssuePriorityUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails authMember) {

        issueManagementService.updateIssuePriority(
                issueId, ctx.projectId(), request.priority(), authMember.getMemberId());

        return ResponseEntity.ok(ApiResponse.success("우선순위가 변경되었습니다."));
    }

    /**
     * 이슈 변경 이력 조회
     *
     * @param ctx 프로젝트 컨텍스트
     * @param issueId 이슈 ID
     * @return 변경 이력 목록 (최신순)
     */
    @Operation(summary = "이슈 변경 이력 조회", description = "특정 이슈의 모든 변경 이력을 최신순으로 조회합니다.")
    @IssueManagementSwaggerDocs.GetHistoriesDocs
    @GetMapping("/{issueId}/histories")
    public ResponseEntity<ApiResponse<List<IssueHistoryResponse>>> getIssueHistories(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId) {

        // 프로젝트 소유권 검증
        issueManagementService.getIssueDetail(issueId, ctx.projectId());

        List<IssueHistory> histories = issueHistoryService.getIssueHistories(issueId);

        List<IssueHistoryResponse> response =
                histories.stream().map(IssueHistoryResponse::from).toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 이슈 분포 분석 데이터 조회
     *
     * @param ctx 프로젝트 컨텍스트
     * @param issueId 이슈 ID
     * @return 분포 분석 데이터 (OS, 버전, 디바이스)
     */
    @Operation(summary = "이슈 분포 분석 조회", description = "이슈의 OS, 앱 버전, 디바이스별 발생 분포를 분석합니다.")
    @GetMapping("/{issueId}/distribution")
    public ResponseEntity<ApiResponse<DistributionDataResponse>> getDistributionAnalysis(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId) {

        DistributionDataResponse distribution =
                distributionAnalysisService.getDistributionData(issueId);

        return ResponseEntity.ok(ApiResponse.success(distribution));
    }

    /**
     * 이슈로 영향받은 플레이어 목록 조회
     *
     * @param ctx 프로젝트 컨텍스트
     * @param issueId 이슈 ID
     * @param pageable 페이지네이션 (기본: page=0, size=20)
     * @return 영향받은 플레이어 목록
     */
    @Operation(
            summary = "영향받은 플레이어 목록 조회",
            description = "특정 이슈로 인해 영향을 받은 플레이어 목록 및 통계를 조회합니다. 발생 횟수 내림차순으로 정렬됩니다.")
    @GetMapping("/{issueId}/affected-players")
    public ResponseEntity<ApiResponse<Page<AffectedPlayerResponse>>> getAffectedPlayers(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<AffectedPlayerResponse> affectedPlayers =
                affectedPlayersService.getAffectedPlayers(issueId, pageable);

        return ResponseEntity.ok(ApiResponse.success(affectedPlayers));
    }

    /**
     * 이슈 발생 추이 조회
     *
     * @param ctx 프로젝트 컨텍스트
     * @param issueId 이슈 ID
     * @param days 조회 기간 (일 단위, 기본: 7일)
     * @return 날짜별 발생 횟수
     */
    @Operation(
            summary = "이슈 발생 추이 조회",
            description = "특정 이슈의 시간대별 발생 추이를 조회합니다. 기본 7일간의 데이터를 제공합니다.")
    @GetMapping("/{issueId}/trend")
    public ResponseEntity<ApiResponse<List<OccurrenceTrendResponse>>> getOccurrenceTrend(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId,
            @Parameter(description = "조회 기간 (일 단위)", example = "7")
                    @RequestParam(defaultValue = "7")
                    int days) {

        List<OccurrenceTrendResponse> trend =
                occurrenceTrendService.getOccurrenceTrend(issueId, days);

        return ResponseEntity.ok(ApiResponse.success(trend));
    }

    /**
     * 이슈 근본 원인 분석
     *
     * @param ctx 프로젝트 컨텍스트
     * @param issueId 이슈 ID
     * @return 근본 원인 분석 결과
     */
    @Operation(summary = "이슈 근본 원인 분석", description = "규칙 기반 패턴 분석을 통해 이슈의 근본 원인 및 해결 방법을 제시합니다.")
    @GetMapping("/{issueId}/root-cause")
    public ResponseEntity<ApiResponse<RootCauseAnalysisResponse>> getRootCauseAnalysis(
            @CurrentProject ProjectRequestContext ctx,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId) {

        RootCauseAnalysisResponse analysis = rootCauseAnalysisService.analyze(issueId);

        return ResponseEntity.ok(ApiResponse.success(analysis));
    }

    /**
     * 이슈 발생 맥락 정보 조회
     *
     * @param ctx 프로젝트 컨텍스트
     * @param issueId 이슈 ID
     * @return 맥락 정보 (환경, 게임 상태)
     */
    @Operation(summary = "이슈 발생 맥락 정보 조회", description = "이슈가 발생한 환경과 게임 상태 정보를 분석하여 제공합니다.")
    @GetMapping("/{issueId}/context")
    public ResponseEntity<
                    ApiResponse<
                            kr.java.documind.domain.issue.model.dto.response.IssueContextResponse>>
            getIssueContext(
                    @CurrentProject ProjectRequestContext ctx,
                    @Parameter(description = "이슈 ID", example = "101", required = true)
                            @PathVariable
                            Long issueId) {

        kr.java.documind.domain.issue.model.dto.response.IssueContextResponse context =
                issueContextService.getIssueContext(issueId);

        return ResponseEntity.ok(ApiResponse.success(context));
    }
}

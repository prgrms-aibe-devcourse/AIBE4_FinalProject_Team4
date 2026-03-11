package kr.java.documind.domain.issue.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import kr.java.documind.domain.issue.model.dto.request.IssueAssignRequest;
import kr.java.documind.domain.issue.model.dto.request.IssueStatusUpdateRequest;
import kr.java.documind.domain.issue.model.dto.response.IssueHistoryResponse;
import kr.java.documind.domain.issue.model.entity.IssueHistory;
import kr.java.documind.domain.issue.service.workflow.IssueHistoryService;
import kr.java.documind.domain.issue.service.workflow.IssueManagementService;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Issue Management", description = "이슈 관리 API (담당자 할당, 상태 변경, 이력 조회)")
@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueManagementApiController {

    private final IssueManagementService issueManagementService;
    private final IssueHistoryService issueHistoryService;

    /**
     * 이슈 담당자 지정
     *
     * @param issueId 이슈 ID
     * @param request 담당자 ID
     * @param authMember 현재 로그인한 사용자 (변경자)
     * @return 성공 메시지
     */
    @Operation(summary = "이슈 담당자 지정", description = "이슈에 담당자를 할당하고 변경 이력을 기록합니다.")
    @IssueManagementSwaggerDocs.AssignIssueDocs
    @PutMapping("/{issueId}/assignee")
    public ResponseEntity<ApiResponse<Void>> assignIssue(
            @Parameter(description = "이슈 ID", example = "101", required = true)
                    @PathVariable
                    Long issueId,
            @Valid @RequestBody IssueAssignRequest request,
            @AuthenticationPrincipal CustomUserDetails authMember) {

        issueManagementService.assignIssue(
                issueId, request.assigneeId(), authMember.getMemberId());

        return ResponseEntity.ok(ApiResponse.success("담당자가 지정되었습니다."));
    }

    /**
     * 이슈 상태 변경
     *
     * @param issueId 이슈 ID
     * @param request 변경할 상태 및 패치노트 포함 여부
     * @param authMember 현재 로그인한 사용자 (변경자)
     * @return 성공 메시지
     */
    @Operation(
            summary = "이슈 상태 변경",
            description =
                    "이슈 상태를 변경하고 변경 이력을 기록합니다. "
                            + "RESOLVED 상태로 변경 시 패치노트 반영 여부를 선택할 수 있습니다.")
    @IssueManagementSwaggerDocs.UpdateStatusDocs
    @PutMapping("/{issueId}/status")
    public ResponseEntity<ApiResponse<Void>> updateIssueStatus(
            @Parameter(description = "이슈 ID", example = "101", required = true)
                    @PathVariable
                    Long issueId,
            @Valid @RequestBody IssueStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails authMember) {

        issueManagementService.updateIssueStatus(
                issueId,
                request.status(),
                authMember.getMemberId(),
                request.shouldIncludeInPatchNote());

        return ResponseEntity.ok(ApiResponse.success("이슈 상태가 변경되었습니다."));
    }

    /**
     * 이슈 변경 이력 조회
     *
     * @param issueId 이슈 ID
     * @return 변경 이력 목록 (최신순)
     */
    @Operation(summary = "이슈 변경 이력 조회", description = "특정 이슈의 모든 변경 이력을 최신순으로 조회합니다.")
    @IssueManagementSwaggerDocs.GetHistoriesDocs
    @GetMapping("/{issueId}/histories")
    public ResponseEntity<ApiResponse<List<IssueHistoryResponse>>> getIssueHistories(
            @Parameter(description = "이슈 ID", example = "101", required = true)
                    @PathVariable
                    Long issueId) {

        List<IssueHistory> histories = issueHistoryService.getIssueHistories(issueId);

        List<IssueHistoryResponse> response =
                histories.stream().map(IssueHistoryResponse::from).toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

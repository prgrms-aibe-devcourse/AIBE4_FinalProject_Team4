package kr.java.documind.domain.issue.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.model.dto.request.CommentCreateRequest;
import kr.java.documind.domain.issue.model.dto.request.CommentUpdateRequest;
import kr.java.documind.domain.issue.model.dto.response.CommentResponse;
import kr.java.documind.domain.issue.service.IssueCommentService;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.response.PageResponses;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Issue Comment", description = "이슈 댓글 API (생성, 조회, 수정, 삭제)")
@RestController
@RequestMapping("/api/projects/{projectId}/issues/{issueId}/comments")
@RequiredArgsConstructor
public class IssueCommentApiController {

    private final IssueCommentService issueCommentService;

    /**
     * 댓글 생성
     *
     * @param projectId 프로젝트 ID
     * @param issueId 이슈 ID
     * @param request 댓글 생성 요청
     * @param authMember 현재 로그인한 사용자
     * @return 생성된 댓글
     */
    @Operation(summary = "댓글 생성", description = "이슈에 댓글을 작성합니다. @닉네임 형식으로 프로젝트 멤버를 멘션할 수 있습니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @Parameter(
                            description = "프로젝트 ID",
                            example = "123e4567-e89b-12d3-a456-426614174000",
                            required = true)
                    @PathVariable
                    UUID projectId,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId,
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails authMember) {

        CommentResponse response =
                issueCommentService.createComment(
                        issueId, projectId, authMember.getMemberId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * 댓글 목록 조회
     *
     * @param projectId 프로젝트 ID
     * @param issueId 이슈 ID
     * @param pageable 페이지네이션 (기본: page=0, size=20)
     * @return 댓글 목록
     */
    @Operation(summary = "댓글 목록 조회", description = "이슈의 댓글 목록을 오래된 순으로 조회합니다. 페이지네이션을 지원합니다.")
    @GetMapping
    public ApiResponse<List<CommentResponse>> getComments(
            @Parameter(
                            description = "프로젝트 ID",
                            example = "123e4567-e89b-12d3-a456-426614174000",
                            required = true)
                    @PathVariable
                    UUID projectId,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<CommentResponse> comments =
                issueCommentService.getComments(issueId, projectId, pageable);

        return PageResponses.of(comments);
    }

    /**
     * 댓글 수정
     *
     * @param projectId 프로젝트 ID
     * @param issueId 이슈 ID
     * @param commentId 댓글 ID
     * @param request 댓글 수정 요청
     * @param authMember 현재 로그인한 사용자
     * @return 수정된 댓글
     */
    @Operation(summary = "댓글 수정", description = "자신이 작성한 댓글을 수정합니다. 멘션도 재설정할 수 있습니다.")
    @PutMapping("/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @Parameter(
                            description = "프로젝트 ID",
                            example = "123e4567-e89b-12d3-a456-426614174000",
                            required = true)
                    @PathVariable
                    UUID projectId,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId,
            @Parameter(description = "댓글 ID", example = "1", required = true) @PathVariable
                    Long commentId,
            @Valid @RequestBody CommentUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails authMember) {

        CommentResponse response =
                issueCommentService.updateComment(
                        commentId, issueId, projectId, authMember.getMemberId(), request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 댓글 삭제
     *
     * @param projectId 프로젝트 ID
     * @param issueId 이슈 ID
     * @param commentId 댓글 ID
     * @param authMember 현재 로그인한 사용자
     * @return 성공 메시지
     */
    @Operation(summary = "댓글 삭제", description = "자신이 작성한 댓글을 삭제합니다. 삭제된 댓글은 복구할 수 없습니다.")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @Parameter(
                            description = "프로젝트 ID",
                            example = "123e4567-e89b-12d3-a456-426614174000",
                            required = true)
                    @PathVariable
                    UUID projectId,
            @Parameter(description = "이슈 ID", example = "101", required = true) @PathVariable
                    Long issueId,
            @Parameter(description = "댓글 ID", example = "1", required = true) @PathVariable
                    Long commentId,
            @AuthenticationPrincipal CustomUserDetails authMember) {

        issueCommentService.deleteComment(commentId, issueId, projectId, authMember.getMemberId());

        return ResponseEntity.ok(ApiResponse.success("댓글이 삭제되었습니다."));
    }
}

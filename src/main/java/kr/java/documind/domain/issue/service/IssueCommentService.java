package kr.java.documind.domain.issue.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.java.documind.domain.issue.model.dto.request.CommentCreateRequest;
import kr.java.documind.domain.issue.model.dto.request.CommentUpdateRequest;
import kr.java.documind.domain.issue.model.dto.response.CommentResponse;
import kr.java.documind.domain.issue.model.dto.response.MemberSimpleInfo;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.entity.IssueComment;
import kr.java.documind.domain.issue.model.repository.CommentRepository;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.domain.issue.service.workflow.IssueHistoryService;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.repository.MemberRepository;
import kr.java.documind.domain.member.model.repository.ProjectMemberRepository;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ForbiddenException;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이슈 댓글 서비스
 *
 * <p>댓글 CRUD 및 멘션 관리
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IssueCommentService {

    private final CommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final MemberRepository memberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueHistoryService issueHistoryService;

    /**
     * 댓글 생성
     *
     * @param issueId 이슈 ID
     * @param projectId 프로젝트 ID
     * @param authorId 작성자 ID
     * @param request 댓글 생성 요청
     * @return 생성된 댓글
     */
    @Transactional
    public CommentResponse createComment(
            Long issueId, UUID projectId, UUID authorId, CommentCreateRequest request) {

        // 1. 이슈 존재 및 프로젝트 소유권 확인
        Issue issue =
                issueRepository
                        .findById(issueId)
                        .orElseThrow(() -> new NotFoundException("이슈를 찾을 수 없습니다: " + issueId));

        if (!issue.getProjectId().equals(projectId)) {
            throw new ForbiddenException("해당 프로젝트의 이슈가 아닙니다: " + issueId);
        }

        // 2. 멘션된 멤버 검증 (프로젝트 멤버인지 확인)
        List<UUID> validatedMentionIds =
                validateMentionedMembers(request.mentionedMemberIds(), projectId);

        // 3. Comment 엔티티 생성
        IssueComment issueComment =
                IssueComment.create(issueId, authorId, request.content(), validatedMentionIds);

        // 4. 저장
        commentRepository.save(issueComment);

        log.info(
                "[IssueCommentService] 댓글 생성: issueId={}, authorId={}, mentionCount={}",
                issueId,
                authorId,
                validatedMentionIds.size());

        // 5. 타임라인 기록 저장
        issueHistoryService.saveCommentAdded(issueId, authorId, issueComment.getContent());

        // 6. (향후) 이벤트 발행 - 알림 발송
        // eventPublisher.publish(new CommentCreatedEvent(comment.getId(), issueId, authorId,
        // validatedMentionIds));

        // 7. 응답 생성
        return buildCommentResponse(issueComment);
    }

    /**
     * 댓글 수정
     *
     * @param commentId 댓글 ID
     * @param issueId 이슈 ID
     * @param projectId 프로젝트 ID
     * @param memberId 수정 요청자 ID
     * @param request 댓글 수정 요청
     * @return 수정된 댓글
     */
    @Transactional
    public CommentResponse updateComment(
            Long commentId,
            Long issueId,
            UUID projectId,
            UUID memberId,
            CommentUpdateRequest request) {

        // 1. 댓글 조회
        IssueComment issueComment =
                commentRepository
                        .findById(commentId)
                        .orElseThrow(() -> new NotFoundException("댓글을 찾을 수 없습니다: " + commentId));

        // 2. 이슈 일치 확인
        if (!issueComment.getIssueId().equals(issueId)) {
            throw new BadRequestException("해당 이슈의 댓글이 아닙니다");
        }

        // 3. 이슈 프로젝트 소유권 확인
        Issue issue =
                issueRepository
                        .findById(issueId)
                        .orElseThrow(() -> new NotFoundException("이슈를 찾을 수 없습니다: " + issueId));

        if (!issue.getProjectId().equals(projectId)) {
            throw new ForbiddenException("해당 프로젝트의 이슈가 아닙니다");
        }

        // 4. 작성자 본인 확인
        if (!issueComment.getMemberId().equals(memberId)) {
            throw new ForbiddenException("자신이 작성한 댓글만 수정할 수 있습니다");
        }

        // 5. 멘션 재검증
        List<UUID> validatedMentionIds =
                validateMentionedMembers(request.mentionedMemberIds(), projectId);

        // 6. 댓글 수정
        issueComment.updateContent(request.content(), validatedMentionIds);

        log.info(
                "[IssueCommentService] 댓글 수정: commentId={}, memberId={}, mentionCount={}",
                commentId,
                memberId,
                validatedMentionIds.size());

        // 7. 응답 생성
        return buildCommentResponse(issueComment);
    }

    /**
     * 댓글 삭제 (하드 딜리트)
     *
     * @param commentId 댓글 ID
     * @param issueId 이슈 ID
     * @param projectId 프로젝트 ID
     * @param memberId 삭제 요청자 ID
     */
    @Transactional
    public void deleteComment(Long commentId, Long issueId, UUID projectId, UUID memberId) {

        // 1. 댓글 조회
        IssueComment issueComment =
                commentRepository
                        .findById(commentId)
                        .orElseThrow(() -> new NotFoundException("댓글을 찾을 수 없습니다: " + commentId));

        // 2. 이슈 일치 확인
        if (!issueComment.getIssueId().equals(issueId)) {
            throw new BadRequestException("해당 이슈의 댓글이 아닙니다");
        }

        // 3. 이슈 프로젝트 소유권 확인
        Issue issue =
                issueRepository
                        .findById(issueId)
                        .orElseThrow(() -> new NotFoundException("이슈를 찾을 수 없습니다: " + issueId));

        if (!issue.getProjectId().equals(projectId)) {
            throw new ForbiddenException("해당 프로젝트의 이슈가 아닙니다");
        }

        // 4. 작성자 본인 확인
        if (!issueComment.getMemberId().equals(memberId)) {
            throw new ForbiddenException("자신이 작성한 댓글만 삭제할 수 있습니다");
        }

        // 5. 하드 딜리트
        commentRepository.deleteByIdAndIssueId(commentId, issueId);

        log.info(
                "[IssueCommentService] 댓글 삭제: commentId={}, issueId={}, memberId={}",
                commentId,
                issueId,
                memberId);
    }

    /**
     * 이슈별 댓글 목록 조회 (페이지네이션)
     *
     * @param issueId 이슈 ID
     * @param projectId 프로젝트 ID
     * @param pageable 페이지네이션
     * @return 댓글 목록
     */
    public Page<CommentResponse> getComments(Long issueId, UUID projectId, Pageable pageable) {

        // 1. 이슈 존재 및 프로젝트 소유권 확인
        Issue issue =
                issueRepository
                        .findById(issueId)
                        .orElseThrow(() -> new NotFoundException("이슈를 찾을 수 없습니다: " + issueId));

        if (!issue.getProjectId().equals(projectId)) {
            throw new ForbiddenException("해당 프로젝트의 이슈가 아닙니다: " + issueId);
        }

        // 2. 댓글 목록 조회 (페이징, 오래된 순)
        Page<IssueComment> page =
                commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId, pageable);

        // 3. DTO 변환 (작성자 및 멘션 정보 포함)
        List<CommentResponse> responses =
                page.getContent().stream().map(this::buildCommentResponse).toList();

        return new PageImpl<>(responses, pageable, page.getTotalElements());
    }

    /**
     * 멘션된 멤버 검증 (프로젝트 활성 멤버인지 확인) - 배치 조회
     *
     * @param mentionedMemberIds 멘션된 멤버 ID 목록
     * @param projectId 프로젝트 ID
     * @return 검증된 멤버 ID 목록
     */
    private List<UUID> validateMentionedMembers(List<UUID> mentionedMemberIds, UUID projectId) {
        if (mentionedMemberIds == null || mentionedMemberIds.isEmpty()) {
            return List.of();
        }

        Set<UUID> validMemberIds =
                new HashSet<>(
                        projectMemberRepository.findValidMemberIds(
                                projectId, mentionedMemberIds, AccountStatus.ACTIVE));

        // 유효하지 않은 멘션은 로그로 기록
        mentionedMemberIds.stream()
                .filter(id -> !validMemberIds.contains(id))
                .forEach(
                        id ->
                                log.warn(
                                        "[IssueCommentService] 멘션 무시 (프로젝트 멤버 아님): memberId={}, projectId={}",
                                        id,
                                        projectId));

        // 입력 순서 유지
        return mentionedMemberIds.stream().filter(validMemberIds::contains).toList();
    }

    /**
     * Comment 엔티티 → CommentResponse 변환 (작성자 및 멘션 정보 포함)
     *
     * @param issueComment Comment 엔티티
     * @return CommentResponse
     */
    private CommentResponse buildCommentResponse(IssueComment issueComment) {
        // 작성자 정보 조회
        Member author =
                memberRepository.findById(issueComment.getMemberId()).orElse(null); // 탈퇴한 사용자일 수 있음
        MemberSimpleInfo authorInfo = author != null ? MemberSimpleInfo.from(author) : null;

        // 멘션된 사용자 정보 조회
        List<MemberSimpleInfo> mentionedMembers = List.of();
        if (issueComment.getMentionedMemberIds() != null
                && !issueComment.getMentionedMemberIds().isEmpty()) {
            List<Member> members =
                    memberRepository.findAllById(issueComment.getMentionedMemberIds());

            // UUID 순서 보존을 위한 Map 사용
            Map<UUID, Member> memberMap =
                    members.stream().collect(Collectors.toMap(Member::getId, Function.identity()));

            mentionedMembers =
                    issueComment.getMentionedMemberIds().stream()
                            .map(memberMap::get)
                            .filter(m -> m != null) // 탈퇴한 사용자 제외
                            .map(MemberSimpleInfo::from)
                            .toList();
        }

        return CommentResponse.from(issueComment, authorInfo, mentionedMembers);
    }
}
